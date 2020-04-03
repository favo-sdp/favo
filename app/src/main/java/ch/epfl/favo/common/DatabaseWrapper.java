package ch.epfl.favo.common;

import android.annotation.SuppressLint;

import androidx.annotation.NonNull;

import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentSnapshot;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;
import com.google.firebase.firestore.QuerySnapshot;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import ch.epfl.favo.util.TaskToFutureAdapter;

@SuppressLint("NewApi")
public class DatabaseWrapper {

  private static DatabaseWrapper INSTANCE = null;
  private FirebaseFirestore firestore;

  // final fields regarding ID generation
  private static final String ID_CHARS = "ABCDEFGHIJKLMNOPQRSTUVWXYZ0123456789";
  private static final int ID_LENGTH = 25;

  private DatabaseWrapper() {
    FirebaseFirestore.setLoggingEnabled(true);
    FirebaseFirestoreSettings settings =
        new FirebaseFirestoreSettings.Builder().setPersistenceEnabled(true).build();
    try {
      firestore = FirebaseFirestore.getInstance();
      firestore.setFirestoreSettings(settings);
    } catch (Exception e) {
      throw e;
    }
  }

  private static DatabaseWrapper getInstance() {
    if (INSTANCE == null) {
      INSTANCE = new DatabaseWrapper();
    }
    return INSTANCE;
  }

  public static String generateRandomId() {

    StringBuilder sb = new StringBuilder(ID_LENGTH);
    for (int i = 0; i < ID_LENGTH; i++) {
      int index = (int) (36 * Math.random());
      sb.append(ID_CHARS.charAt(index));
    }
    return sb.toString();
  }

  static <T extends Document> void addDocument(T document, String collection) {
    getCollectionReference(collection).document(document.getId()).set(document);
  }

  static <T extends Document> void removeDocument(String key, String collection) {
    getCollectionReference(collection).document(key).delete();
  }

  static <T extends Document> void updateDocument(
      String key, Map<String, Object> updates, String collection) {
    getCollectionReference(collection).document(key).update(updates);
  }

  static <T extends Document> CompletableFuture<T> getDocument(
      String key, Class<T> cls, String collection) throws RuntimeException {

    CompletableFuture<T> future = new CompletableFuture<>();

    getCollectionReference(collection)
      .document(key)
      .get()
      .addOnCompleteListener(task -> {
        if (task.isSuccessful()) {
          DocumentSnapshot documentSnapshot = task.getResult();
          if (documentSnapshot.exists()) {
            future.complete(documentSnapshot.toObject(cls));
          } else {
            future.completeExceptionally(new RuntimeException("document does not exist"));
          }
        } else {
          future.completeExceptionally(new RuntimeException("firebase error"));
        }

      });

    return future;

//    Task<DocumentSnapshot> getTask = getCollectionReference(collection).document(key).get();
//    CompletableFuture<DocumentSnapshot> getFuture = new TaskToFutureAdapter<>(getTask);
//
//    return getFuture.thenApply(
//        documentSnapshot -> {
//          if (documentSnapshot.exists()) {
//            return documentSnapshot.toObject(cls);
//          } else {
//            throw new RuntimeException(String.format("Document %s does not exist ", key));
//          }
//        });
  }

  static <T extends Document> CompletableFuture<List<T>> getAllDocuments(
      Class<T> cls, String collection) {
    Task<QuerySnapshot> getAllTask = getCollectionReference(collection).get();
    CompletableFuture<QuerySnapshot> getAllFuture = new TaskToFutureAdapter<>(getAllTask);

    return getAllFuture.thenApply(
        querySnapshot -> {
          List<T> values = new ArrayList<>();
          for (DocumentSnapshot documentSnapshot : querySnapshot) {
            values.add(documentSnapshot.toObject(cls));
          }
          return values;
        });
  }

  private static CollectionReference getCollectionReference(String collection) {
    return getInstance().firestore.collection(collection);
  }

  public interface DocumentCallback<T> {
    void onCallback(T value);
  }

  public static <T extends Document> void getDocumentCallback(
    String key, Class<T> cls, String collection, DocumentCallback callback) throws RuntimeException {
    getCollectionReference(collection)
      .document(key)
      .get()
      .addOnCompleteListener(task -> {
        if (task.isSuccessful()) {
          DocumentSnapshot documentSnapshot = task.getResult();
          if (documentSnapshot.exists()) {
            callback.onCallback(documentSnapshot.toObject(cls));
          } else {
            throw new RuntimeException("document does not exist");
          }
        } else {
          throw new RuntimeException("firebase error");
        }
      });
  }
}
