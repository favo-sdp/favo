package ch.epfl.favo.view.tabs.addFavor;

import android.Manifest;
import android.annotation.SuppressLint;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffColorFilter;
import android.graphics.drawable.BitmapDrawable;
import android.hardware.Camera;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputFilter;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ImageView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.firebase.auth.FirebaseUser;

import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Function;

import ch.epfl.favo.R;
import ch.epfl.favo.favor.Favor;
import ch.epfl.favo.favor.FavorStatus;
import ch.epfl.favo.gps.FavoLocation;
import ch.epfl.favo.gps.IGpsTracker;
import ch.epfl.favo.user.UserUtil;
import ch.epfl.favo.util.CommonTools;
import ch.epfl.favo.util.DependencyFactory;
import ch.epfl.favo.viewmodel.IFavorViewModel;

import static android.app.Activity.RESULT_OK;
import static androidx.navigation.Navigation.findNavController;
import static ch.epfl.favo.util.CommonTools.hideSoftKeyboard;

@SuppressLint("NewApi")
public class FavorEditingView extends Fragment {
  private static String TAG = "FavorEditingView";

  private static final int PICK_IMAGE_REQUEST = 1;
  private static final int USE_CAMERA_REQUEST = 2;

  private IFavorViewModel favorViewModel;

  private FavorStatus favorStatus;
  private ImageView mImageView;
  private EditText mTitleView;
  private EditText mDescriptionView;
  private EditText mFavoCoinsView;
  private IGpsTracker mGpsTracker;
  private Favor currentFavor;
  private String favorSource;
  private MenuItem requestItem;
  private FirebaseUser currentUser;

  public FavorEditingView() {
    // Required empty public constructor
  }

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    setHasOptionsMenu(true);
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    View rootView = inflater.inflate(R.layout.fragment_favor_editing_view, container, false);
    setupButtons(rootView);

    setupToolBar();

    favorStatus = FavorStatus.EDIT;
    currentUser = DependencyFactory.getCurrentFirebaseUser();

    mTitleView = rootView.findViewById(R.id.title_request_view);
    mDescriptionView = rootView.findViewById(R.id.details);
    setupView(rootView);
    // Extract other elements
    mImageView = rootView.findViewById(R.id.image_view_request_view);

    mFavoCoinsView = rootView.findViewById(R.id.favor_reward);

    UserUtil.getSingleInstance()
        .findUser(DependencyFactory.getCurrentFirebaseUser().getUid())
        .thenAccept(
            user ->
                mFavoCoinsView.setFilters(
                    new InputFilter[] {new InputFilterMinMax(0, (int) user.getBalance())}));

    // Get dependencies
    mGpsTracker = DependencyFactory.getCurrentGpsTracker(requireActivity().getApplicationContext());
    // Inject argument
    favorViewModel =
        (IFavorViewModel)
            new ViewModelProvider(requireActivity())
                .get(DependencyFactory.getCurrentViewModelClass());

    if (getArguments() != null) {
      currentFavor = getArguments().getParcelable(CommonTools.FAVOR_VALUE_ARGS);
      favorSource = getArguments().getString(CommonTools.FAVOR_SOURCE);
      currentFavor.setStatusIdToInt(FavorStatus.EDIT);
      displayFavorInfo(rootView);
      if (favorSource.equals(getString(R.string.favor_source_publishedFavor))) {
        setupFavorListener(rootView, currentFavor.getId());
      }
    } else favorSource = getString(R.string.favor_source_floatButton);
    return rootView;
  }

  private void setupFavorListener(View rootView, String favorId) {
    getViewModel()
        .setObservedFavor(favorId)
        .observe(
            getViewLifecycleOwner(),
            favor -> {
              try {
                if (favor != null && favor.getId().equals(favorId)) {
                  if (favor.getUserIds().size() > currentFavor.getUserIds().size()) {
                    currentFavor = favor;
                    CommonTools.showSnackbar(
                        rootView, getString(R.string.old_favor_accepted_by_others));
                  } else if (favor.getUserIds().size() < currentFavor.getUserIds().size()) {
                    currentFavor = favor;
                    CommonTools.showSnackbar(
                        rootView, getString(R.string.old_favor_cancelled_by_others));
                  }
                } else throw new RuntimeException(getString(R.string.error_database_sync));
              } catch (Exception e) {
                Log.d(TAG, e.getMessage());
                CommonTools.showSnackbar(rootView, getString(R.string.error_database_sync));
              }
            });
  }

  public IFavorViewModel getViewModel() {
    return favorViewModel;
  }

  /** When fragment is launched with favor. */
  private void displayFavorInfo(View v) {
    favorStatus = FavorStatus.toEnum(currentFavor.getStatusId());
    mTitleView.setText(currentFavor.getTitle());
    mDescriptionView.setText(currentFavor.getDescription());
    mFavoCoinsView.setText(String.valueOf((int) currentFavor.getReward()));

    String url = currentFavor.getPictureUrl();
    if (url != null) {
      if (mImageView.getDrawable() != null) {
        v.findViewById(R.id.loading_panel).setVisibility(View.VISIBLE);
      }
      getViewModel()
          .downloadPicture(currentFavor)
          .thenAccept(
              picture -> {
                mImageView.setImageBitmap(picture);
                v.findViewById(R.id.loading_panel).setVisibility(View.GONE);
              });
    }
  }

  /** Identifes buttons and sets onclick listeners. */
  private void setupButtons(View rootView) {

    // Button: Add Image from files
    ImageButton addPictureFromFilesBtn = rootView.findViewById(R.id.add_picture_button);
    addPictureFromFilesBtn.setOnClickListener(new onButtonClick());

    // Button: Add picture from camera
    ImageButton addPictureFromCameraBtn = rootView.findViewById(R.id.add_camera_picture_button);
    addPictureFromCameraBtn.setOnClickListener(new onButtonClick());
    if (!isCameraAvailable()) { // if camera is not available
      addPictureFromCameraBtn.setEnabled(false);
    }

    // Button: Access location
    //    EditText locationAccessBtn = rootView.findViewById(R.id.location_request_view_btn);
    //    locationAccessBtn.setOnClickListener(new onButtonClick());
  }

  /**
   * Method is called when request favor button is clicked. It uploads favor request to the database
   * and updates view so that favor is editable.
   */
  private void requestFavor() {
    // update currentFavor

    EditText titleElem = requireView().findViewById(R.id.title_request_view);
    if (titleElem.getText().toString().equals("")) {
      CommonTools.showSnackbar(requireView(), getString(R.string.title_required_message));
    } else {

      getFavorFromView();
      CommonTools.hideSoftKeyboard(requireActivity());

      new AlertDialog.Builder(requireActivity())
          .setMessage(getText(R.string.set_location_message))
          .setPositiveButton(
              getText(R.string.set_location_yes),
              (dialogInterface, i) -> {
                getFavorFromView();
                CommonTools.hideSoftKeyboard(requireActivity());
                favorViewModel.setShowObservedFavor(true);
                favorViewModel.setFavorValue(currentFavor);
                // signal the destination is map view
                findNavController(requireActivity(), R.id.nav_host_fragment)
                    .navigate(R.id.action_favorEditingView_to_nav_map, null);
              })
          .setNegativeButton(
              getText(R.string.set_location_no),
              (dialogInterface, i) -> {
                favorStatus = FavorStatus.REQUESTED;
                currentFavor.setStatusIdToInt(FavorStatus.REQUESTED);
                // post to DB
                CompletableFuture postFavorFuture = getViewModel().requestFavor(currentFavor);
                postFavorFuture.thenAccept(onSuccessfulRequest(requireView()));
                postFavorFuture.exceptionally(onFailedResult(requireView()));
                // Show confirmation and minimize keyboard
                if (DependencyFactory.isOfflineMode(requireContext())) {
                  CommonTools.showSnackbar(requireView(), getString(R.string.save_draft_message));
                }
              })
          .show();
    }
  }

  /** Extracts favor data from and assigns it to currentFavor. */
  private void getFavorFromView() {

    // Extract details and post favor to Firebase
    String title = mTitleView.getText().toString();
    String desc = mDescriptionView.getText().toString();
    String rewardString = mFavoCoinsView.getText().toString();

    double reward = 0;
    if (!rewardString.equals("")) {
      reward = Double.parseDouble(rewardString);
    }

    FavoLocation loc = new FavoLocation(mGpsTracker.getLocation());
    // if a favor is initiated from map, then override the current location
    // with location got from map( already saved in currentFavor )
    if (favorSource.equals(getString(R.string.favor_source_map))) {
      loc.setLongitude(currentFavor.getLocation().getLongitude());
      loc.setLatitude(currentFavor.getLocation().getLatitude());
    }

    Favor favor = new Favor(title, desc, currentUser.getUid(), loc, favorStatus, reward);
    favor.setRequesterNotifId(DependencyFactory.getCurrentFirebaseNotificationInstanceId().getToken());
    if (currentFavor == null) currentFavor = favor;
    else {
      // do not override the pictureUrl of currentFavor
      favor.setPictureUrl(currentFavor.getPictureUrl());
      currentFavor.updateToOther(favor);
    }
  }

  private void savePicture() {
    Favor favorForPicture;
    // empty field will be override later
    // if currentFavor is null, start from a new one, otherwise use the current one
    getFavorFromView();

    if (currentFavor == null)
      favorForPicture = new Favor("", "", currentUser.getUid(), null, favorStatus, 0);
    else favorForPicture = currentFavor;
    Bitmap picture = ((BitmapDrawable) mImageView.getDrawable()).getBitmap();
    CompletableFuture<Bitmap> cachedPictureFuture =
        getViewModel().loadPictureFromLocal(getContext(), favorForPicture);
    if (cachedPictureFuture != null)
      cachedPictureFuture.thenAccept(
          cachedPicture -> {
            // include the case where cachedPicture is null, because there is no local cache
            if (!picture.sameAs(cachedPicture)) {
              // Upload picture to database if it exists
              getViewModel().uploadOrUpdatePicture(favorForPicture, picture);
              getViewModel().savePictureToLocal(getContext(), favorForPicture, picture);
            }
          });
    currentFavor = favorForPicture;
  }

  /**
   * Called when upload file from storage button is clicked. Method calls external fileChooser
   * intent.
   */
  public void openFileChooser() {
    Intent openFileChooserIntent = new Intent();
    openFileChooserIntent.setType("image/*");
    openFileChooserIntent.setAction(Intent.ACTION_GET_CONTENT);
    startActivityForResult(openFileChooserIntent, PICK_IMAGE_REQUEST);
  }

  /** Called when camera button is clicked Method calls camera intent. */
  private void takePicture() {
    if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
        != PackageManager.PERMISSION_GRANTED) {
      requireActivity()
          .requestPermissions(new String[] {Manifest.permission.CAMERA}, USE_CAMERA_REQUEST);
    } else {
      Intent takePictureIntent = DependencyFactory.getCurrentCameraIntent();

      if (takePictureIntent.resolveActivity(requireActivity().getPackageManager()) != null) {
        startActivityForResult(takePictureIntent, USE_CAMERA_REQUEST);
      }
    }
  }

  private boolean isCameraAvailable() {
    boolean hasCamera =
        requireActivity().getPackageManager().hasSystemFeature(PackageManager.FEATURE_CAMERA_ANY);
    int numberOfCameras = Camera.getNumberOfCameras();
    return (hasCamera && numberOfCameras != 0);
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
    switch (requestCode) {
      case PICK_IMAGE_REQUEST:
        {
          Uri mImageUri = data.getData();
          mImageView.setImageURI(mImageUri);
          break;
        }
      case USE_CAMERA_REQUEST:
        {
          Bundle extras = data.getExtras();
          Bitmap imageBitmap = (Bitmap) extras.get("data");
          mImageView.setImageBitmap(imageBitmap);
          break;
        }
    }
    savePicture();
  }

  /**
   * ensures keyboard hides when user clicks outside of edit texts.
   *
   * @param view corresponds to root view created during onCreate
   */
  @SuppressLint("ClickableViewAccessibility")
  private void setupView(View view) {
    // ensure click on view will hide keyboard
    view.findViewById(R.id.fragment_favor)
        .setOnTouchListener(
            (v, event) -> {
              hideSoftKeyboard(requireActivity());
              return false;
            });
  }

  private Consumer onSuccessfulRequest(View currentView) {
    return o -> {
      CommonTools.showSnackbar(
          currentView,
          getString(CommonTools.getSnackbarMessageForRequestedFavor(requireContext())));

      // jump to favorPublished view
      Bundle favorBundle = new Bundle();
      favorBundle.putString(CommonTools.FAVOR_ARGS, currentFavor.getId());
      int action;
      // if this favor restarts from an archived one, then prevent pressback from jumping to
      // archived favor view.
      if (favorSource.equals(getString(R.string.favor_source_publishedFavor))
          || favorSource.equals(getString(R.string.restart_request)))
        action = R.id.action_nav_favorEditingViewAfterReEnable_to_favorPublishedView;
      else action = R.id.action_nav_favorEditingView_to_favorPublishedView;
      Navigation.findNavController(currentView).navigate(action, favorBundle);
    };
  }

  private Function onFailedResult(View currentView) {
    return (exception) -> {
      CommonTools.showSnackbar(
          currentView,
          getString(
              CommonTools.getSnackbarMessageForFailedRequest((CompletionException) exception)));
      return null;
    };
  }

  class onButtonClick implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      switch (v.getId()) {
        case R.id.add_camera_picture_button:
          takePicture();
          break;
        case R.id.add_picture_button:
          openFileChooser();
          break;
      }
    }
  }

  private void setupToolBar() {
    Toolbar toolbar = requireActivity().findViewById(R.id.toolbar_main_activity);
    toolbar.setTitleTextColor(Color.WHITE);
    toolbar.setBackgroundColor(getResources().getColor(R.color.material_green_500));
    toolbar.setTitle(R.string.request_page_title);
    Objects.requireNonNull(toolbar.getNavigationIcon())
        .setColorFilter(new PorterDuffColorFilter(Color.WHITE, PorterDuff.Mode.SRC_ATOP));
  }

  @Override
  public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {

    // Inflate the menu; this adds items to the action bar if it is present.
    inflater.inflate(R.menu.request_view_menu, menu);

    requestItem = menu.findItem(R.id.request_button);

    if (DependencyFactory.isOfflineMode(requireContext())) {
      requestItem.setTitle(R.string.request_favor_draft);
    }

    SpannableString spanString = new SpannableString(requestItem.getTitle().toString());
    spanString.setSpan(new ForegroundColorSpan(Color.WHITE), 0, spanString.length(), 0);
    requestItem.setTitle(spanString);

    super.onCreateOptionsMenu(menu, inflater);
  }

  // handle button activities
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    if (item.getItemId() == R.id.request_button) {
      requestFavor();
    }
    return super.onOptionsItemSelected(item);
  }
}