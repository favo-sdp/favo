package ch.epfl.favo.view.tabs;

import android.annotation.SuppressLint;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;

import com.bumptech.glide.Glide;
import com.firebase.ui.auth.AuthUI;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseUser;

import ch.epfl.favo.R;
import ch.epfl.favo.auth.SignInActivity;
import ch.epfl.favo.user.User;
import ch.epfl.favo.user.UserUtil;
import ch.epfl.favo.util.CommonTools;
import ch.epfl.favo.util.DependencyFactory;
import ch.epfl.favo.view.tabs.addFavor.PictureUploadButtons;
import ch.epfl.favo.viewmodel.IFavorViewModel;

import static android.app.Activity.RESULT_OK;

@SuppressLint("NewApi")
public class UserAccountPage extends Fragment {

  private static final int PICK_IMAGE_REQUEST = 1;
  private static final int USE_CAMERA_REQUEST = 2;

  private View view;
  private IFavorViewModel viewModel;

  private ImageView profilePictureInput;

  public UserAccountPage() {
    // Required empty public constructor
  }

  @RequiresApi(api = Build.VERSION_CODES.N)
  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    // Inflate the layout for this fragment
    view = inflater.inflate(R.layout.fragment_user_account, container, false);

    String currentUserId = DependencyFactory.getCurrentFirebaseUser().getUid();

    setupButtons();

    displayUserDetails(new User(null, "Name", "Email", null, null, null));

    viewModel =
        (IFavorViewModel)
            new ViewModelProvider(requireActivity())
                .get(DependencyFactory.getCurrentViewModelClass());

    UserUtil.getSingleInstance()
      .findUser(currentUserId)
      .whenComplete((user, e) -> displayUserDetails(user))
      .whenComplete((user, e) -> setupEditProfileDialog(inflater, user));

    return view;
  }

  private IFavorViewModel getViewModel() {
    return viewModel;
  }

  private void setupButtons() {
    Button signOutButton = view.findViewById(R.id.sign_out);
    signOutButton.setOnClickListener(this::signOut);

    Button deleteAccountButton = view.findViewById(R.id.delete_account);
    deleteAccountButton.setOnClickListener(this::deleteAccountClicked);
  }

  private void setupEditProfileDialog(LayoutInflater inflater, User user) {
    View profileHolderView = view.findViewById(R.id.user_profile_holder);
    TextView displayNameView = view.findViewById(R.id.user_name);
    ImageView profilePictureView = view.findViewById(R.id.user_profile_picture);

    View dialogView = inflater.inflate(R.layout.edit_profile_details_dialog, null);
    final EditText displayNameInput = dialogView.findViewById(R.id.change_name_dialog_user_input);
    profilePictureInput = dialogView.findViewById(R.id.new_profile_picture);

    PictureUploadButtons.getInstance().setupButtons(requireContext(), requireActivity(), dialogView);

    AlertDialog changeProfileDetailsDialog = new AlertDialog.Builder(requireContext())
      .setView(dialogView)
      .setNegativeButton(R.string.name_change_dialog_negative, ((dialog, which) -> dialog.dismiss()))
      .setPositiveButton(R.string.name_change_dialog_positive, (dialog, which) -> {
        user.setName(displayNameInput.getText().toString());
        UserUtil.getSingleInstance().updateUser(user);
        displayNameView.setText(displayNameInput.getText());
        dialog.dismiss();
      })
      .create();

    profileHolderView.setOnClickListener(v -> {
      displayNameInput.setText(displayNameView.getText());
      changeProfileDetailsDialog.show();
    });
  }

  /**
   * This method is called when external intents are used to load data on view.
   *
   * @param requestCode integer value specifying which intent was launched
   * @param resultCode integer indicating whether intent was successful
   * @param data result from intent. In this case it contains picture data
   */
  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {

    super.onActivityResult(requestCode, resultCode, data);
    // If intent was not succesful
    if (resultCode != RESULT_OK || data == null) {
      CommonTools.showSnackbar(requireView(), getString(R.string.error_msg_image_request_view));
      return;
    }
    System.out.println(1);
    switch (requestCode) {
      case PICK_IMAGE_REQUEST:
      {
        Uri mImageUri = data.getData();
        profilePictureInput.setImageURI(mImageUri);
        break;
      }
      case USE_CAMERA_REQUEST:
      {
        Bundle extras = data.getExtras();
        Bitmap imageBitmap = (Bitmap) extras.get("data");
        profilePictureInput.setImageBitmap(imageBitmap);
        break;
      }
    }
    savePicture();
  }

  private void savePicture() {
    // do this later
  }

  private void displayUserDetails(User user) {
    TextView nameView = view.findViewById(R.id.user_name);
    nameView.setText(user.getName());

    TextView emailView = view.findViewById(R.id.user_email);
    emailView.setText(
        TextUtils.isEmpty(user.getEmail()) ? getText(R.string.no_email_text) : user.getEmail());

    if (user.getProfilePictureUrl() != null) {
      ImageView profilePictureView = view.findViewById(R.id.user_profile_picture);

      Glide.with(this)
        .load(user.getProfilePictureUrl())
        .fitCenter()
        .into(profilePictureView);
    }

    TextView favorsCreatedView = view.findViewById(R.id.user_account_favorsCreated);
    favorsCreatedView.setText(getString(R.string.favors_created_format, user.getRequestedFavors()));

    TextView favorsAcceptedView= view.findViewById(R.id.user_account_favorsAccepted);
    favorsAcceptedView.setText(getString(R.string.favors_accepted_format, user.getAcceptedFavors()));

    TextView favorsCompletedView = view.findViewById(R.id.user_account_favorsCompleted);
    favorsCompletedView.setText(getString(R.string.favors_completed_format, user.getCompletedFavors()));

    TextView accountLikesView = view.findViewById(R.id.user_account_likes);
    accountLikesView.setText(getString(R.string.likes_format, user.getLikes()));

    TextView accountDislikesView = view.findViewById(R.id.user_account_dislikes);
    accountDislikesView.setText(getString(R.string.dislikes_format, user.getDislikes()));
  }

  private void signOut(View view) {
    AuthUI.getInstance()
        .signOut(requireActivity())
        .addOnCompleteListener(task -> onComplete(task, R.string.sign_out_failed));
  }

  private void deleteAccountClicked(View view) {
    new AlertDialog.Builder(requireActivity())
        .setMessage(getText(R.string.delete_account_alert))
        .setPositiveButton(getText(R.string.yes_text), (dialogInterface, i) -> deleteAccount())
        .setNegativeButton(getText(R.string.no_text), null)
        .show();
  }

  private void deleteAccount() {
    AuthUI.getInstance()
        .delete(requireActivity())
        .addOnCompleteListener(task -> onComplete(task, R.string.delete_account_failed));
  }

  private void onComplete(@NonNull Task<Void> task, int errorMessage) {
    if (task.isSuccessful()) {
      startActivity(new Intent(getActivity(), SignInActivity.class));
    } else {
      CommonTools.showSnackbar(getView(), getString(errorMessage));
    }
  }
}
