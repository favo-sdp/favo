package ch.epfl.favo.view.tabs.addFavor;

import android.content.Intent;
import android.location.Location;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;

import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;

import java.util.Objects;

import ch.epfl.favo.MainActivity;
import ch.epfl.favo.R;
import ch.epfl.favo.common.FavoLocation;
import ch.epfl.favo.favor.Favor;
import ch.epfl.favo.favor.FavorUtil;
import ch.epfl.favo.map.Locator;
import ch.epfl.favo.util.DependencyFactory;
import ch.epfl.favo.view.ViewController;

import static android.app.Activity.RESULT_OK;
import static ch.epfl.favo.util.CommonTools.hideKeyboardFrom;

public class FavorRequestView extends Fragment {

  private static final int PICK_IMAGE_REQUEST = 1;
  private ImageView mImageView;
  private Locator mGpsTracker;

  public FavorRequestView() {
    // Required empty public constructor
  }

  @Override
  public View onCreateView(
          LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {

    setupView();
    View rootView = inflater.inflate(R.layout.fragment_favor, container, false);

    // Button: Request Favor
    Button confirmFavorBtn = rootView.findViewById(R.id.request_button);
    confirmFavorBtn.setOnClickListener(v -> requestFavor());

    // Button: Add Image
    Button addPictureBtn = rootView.findViewById(R.id.add_picture_button);
    addPictureBtn.setOnClickListener(v -> openFileChooser());

    // Extract other elements
    mImageView = rootView.findViewById(R.id.imageView);
    mGpsTracker = DependencyFactory.getCurrentGpsTracker(
            Objects.requireNonNull(getActivity()).getApplicationContext());

    return rootView;
  }

  @Override
  public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);

    if (requestCode == PICK_IMAGE_REQUEST
        && resultCode == RESULT_OK && data != null && data.getData() != null) {
      Uri mImageUri = data.getData(); // path of image
      mImageView.setImageURI(mImageUri);
    } else {
      showSnackbar("Try again!");
    }
  }

  private void requestFavor() {
    // Extract details and post favor to Firebase
    EditText titleElem = Objects.requireNonNull(getView()).findViewById(R.id.title);
    EditText descElem = Objects.requireNonNull(getView()).findViewById(R.id.details);
    String title = titleElem.getText().toString();
    String desc = descElem.getText().toString();
    FavoLocation loc = (FavoLocation) mGpsTracker.getLocation();
    Favor favor = new Favor(title, desc, null, loc, 0);
    FavorUtil.getSingleInstance().postFavor(favor);

    // Save the favor to local favorList
    ((MainActivity) Objects.requireNonNull(getActivity())).activeFavorArrayList.add(favor);

    // Show confirmation and minimize keyboard
    showSnackbar(getString(R.string.favor_request_success_msg));
    hideKeyboardFrom(Objects.requireNonNull(getContext()), getView());

    // Go back
    assert getFragmentManager() != null;
    getFragmentManager().popBackStack();
  }

  public void openFileChooser() {
    Intent intent = new Intent();
    intent.setType("image/*");
    intent.setAction(Intent.ACTION_GET_CONTENT);
    startActivityForResult(intent, PICK_IMAGE_REQUEST);
  }

  public void showSnackbar(String errorMessageRes){
    Snackbar.make(Objects.requireNonNull(getView()), errorMessageRes, Snackbar.LENGTH_LONG).show();
  }

  private void setupView() {
    ((ViewController) Objects.requireNonNull(getActivity())).setupViewBotDestTab();
  }
}
