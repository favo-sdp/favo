package ch.epfl.favo.view.tabs.addFavor;

import android.annotation.SuppressLint;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.PopupMenu;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.bumptech.glide.Glide;
import com.google.firebase.dynamiclinks.DynamicLink;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

import ch.epfl.favo.MainActivity;
import ch.epfl.favo.R;
import ch.epfl.favo.favor.Favor;
import ch.epfl.favo.favor.FavorStatus;
import ch.epfl.favo.user.IUserUtil;
import ch.epfl.favo.user.User;
import ch.epfl.favo.util.CommonTools;
import ch.epfl.favo.util.DependencyFactory;
import ch.epfl.favo.view.NonClickableToolbar;
import ch.epfl.favo.view.tabs.MapPage;
import ch.epfl.favo.viewmodel.IFavorViewModel;
import de.hdodenhof.circleimageview.CircleImageView;

import static androidx.navigation.Navigation.findNavController;

@SuppressLint("NewApi")
public class FavorPublishedView extends Fragment {

  private static final String APP_URL_PREFIX = "https://www.favoapp.com/?favorId=</string>";
  private static final String FAVO_DOMAIN = "https://favoapp.page.link</string>";
  private static final String PACKAGE_NAME = "ch.epfl.favo";
  private static final int LIST_ITEM_HEIGHT = 200;
  private FavorStatus favorStatus;
  private Favor currentFavor;
  private Button commitAndCompleteBtn;
  private IFavorViewModel favorViewModel;
  private NonClickableToolbar toolbar;
  private MenuItem cancelItem;
  private MenuItem editItem;
  private MenuItem inviteItem;
  private MenuItem deleteItem;
  private MenuItem cancelCommitItem;
  private MenuItem reportItem;
  private MenuItem reuseItem;
  private boolean isRequestedByCurrentUser;
  private ImageView imageView;
  private String currentUserId;
  private CircleImageView userProfilePicture;

  private final Map<String, User> commitUsers = new HashMap<>();

  public FavorPublishedView() {
    // create favor detail from a favor
  }

  @Override
  public void onCreate(Bundle bundle) {
    super.onCreate(bundle);
    setHasOptionsMenu(true);
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    // inflate view
    View rootView = inflater.inflate(R.layout.fragment_favor_published_view, container, false);
    setupButtons(rootView);

    toolbar = requireActivity().findViewById(R.id.toolbar_main_activity);
    favorViewModel =
        (IFavorViewModel)
            new ViewModelProvider(requireActivity())
                .get(DependencyFactory.getCurrentViewModelClass());
    currentUserId = DependencyFactory.getCurrentFirebaseUser().getUid();
    String favorId = "";
    if (currentFavor != null) favorId = currentFavor.getId();
    if (getArguments() != null) favorId = getArguments().getString(CommonTools.FAVOR_ARGS);
    setupFavorListener(rootView, favorId);
    return rootView;
  }

  @Override
  public void onCreateOptionsMenu(@NonNull Menu menu, MenuInflater inflater) {

    // Inflate the menu; this adds items to the action bar if it is present.
    inflater.inflate(R.menu.favor_published_menu, menu);

    cancelItem = menu.findItem(R.id.cancel_button);
    cancelCommitItem = menu.findItem(R.id.cancel_commit_button);
    editItem = menu.findItem(R.id.edit_button);
    inviteItem = menu.findItem(R.id.share_button);
    deleteItem = menu.findItem(R.id.delete_button);
    reuseItem = menu.findItem(R.id.reuse_button);
    reportItem = menu.findItem(R.id.report_favor_button);
    if (favorStatus != null) updateAppBarMenuDisplay();
    super.onCreateOptionsMenu(menu, inflater);
  }

  private void updateAppBarMenuDisplay() {
    // if one of the Item is not ready, then all of them are not ready, just return
    if (cancelItem == null) return;
    // if requester has committed this favor, then he can cancel commit
    cancelCommitItem.setVisible(favorStatus == FavorStatus.REQUESTED && isPotentialHelper());

    boolean cancelVisible =
        (favorStatus == FavorStatus.REQUESTED && isRequestedByCurrentUser)
            || favorStatus == FavorStatus.ACCEPTED
            || favorStatus == FavorStatus.COMPLETED_ACCEPTER
            || favorStatus == FavorStatus.COMPLETED_REQUESTER;
    cancelItem.setVisible(cancelVisible);

    deleteItem.setVisible(
        currentFavor.getIsArchived()
            && isRequestedByCurrentUser
            && currentFavor.getAccepterId() == null);
    editItem.setVisible(isRequestedByCurrentUser && favorStatus == FavorStatus.REQUESTED);
    inviteItem.setVisible(favorStatus == FavorStatus.REQUESTED);
    reportItem.setVisible(!isRequestedByCurrentUser);
    reuseItem.setVisible(currentFavor.getIsArchived() && isRequestedByCurrentUser);
  }

  // handle button activities
  @Override
  public boolean onOptionsItemSelected(MenuItem item) {
    switch (item.getItemId()) {
      case R.id.cancel_button:
        cancelFavor();
        break;
      case R.id.cancel_commit_button:
        cancelCommit();
        break;
      case R.id.edit_button:
        goEditFavor();
        break;
      case R.id.share_button:
        onShareClicked();
        break;
      case R.id.delete_button:
        deleteFavor();
        break;
      case R.id.reuse_button:
        reuseFavor();
        break;
      case R.id.report_favor_button:
        reportFavor();
        break;
    }
    return super.onOptionsItemSelected(item);
  }

  private void reportFavor() {
    // TODO: decide what to do with reported favors
    CommonTools.showSnackbar(getView(), getString(R.string.report_favor_message));
  }

  public IFavorViewModel getViewModel() {
    return favorViewModel;
  }

  private void setupFavorListener(View rootView, String favorId) {
    getViewModel()
        .setObservedFavor(favorId)
        .observe(
            getViewLifecycleOwner(),
            favor -> {
              try {
                if (favor != null && favor.getId().equals(favorId)) {
                  currentFavor = favor;
                  displayFromFavor(rootView, currentFavor);
                }
              } catch (Exception e) {
                CommonTools.showSnackbar(rootView, getString(R.string.error_database_sync));
                showBottomBar(false);
              }
            });
  }

  private void onShareClicked() {
    Uri baseUrl = Uri.parse(APP_URL_PREFIX + currentFavor.getId());

    DynamicLink link =
        FirebaseDynamicLinks.getInstance()
            .createDynamicLink()
            .setLink(baseUrl)
            .setDomainUriPrefix(FAVO_DOMAIN)
            .setAndroidParameters(new DynamicLink.AndroidParameters.Builder(PACKAGE_NAME).build())
            .setSocialMetaTagParameters(
                new DynamicLink.SocialMetaTagParameters.Builder()
                    .setTitle("Favor " + currentFavor.getTitle())
                    .setDescription(getString(R.string.share_tip_checkout_favor))
                    .build())
            .buildDynamicLink();

    ((MainActivity) requireActivity()).startShareIntent(link.getUri().toString());
  }

  private void setupButtons(View rootView) {
    commitAndCompleteBtn = rootView.findViewById(R.id.commit_complete_button);
    Button chatBtn = rootView.findViewById(R.id.chat_button);
    TextView locationAccessBtn = rootView.findViewById(R.id.location);
    userProfilePicture = rootView.findViewById(R.id.user_profile_picture);
    TextView userName = rootView.findViewById(R.id.user_name_published_view);
    locationAccessBtn.setOnClickListener(new onButtonClick());
    commitAndCompleteBtn.setOnClickListener(new onButtonClick());
    chatBtn.setOnClickListener(new onButtonClick());
    userProfilePicture.setOnClickListener(new onButtonClick());
    userName.setOnClickListener(new onButtonClick());
  }

  class onButtonClick implements View.OnClickListener {
    @Override
    public void onClick(View v) {
      switch (v.getId()) {
        case R.id.chat_button:
          Navigation.findNavController(requireView())
              .navigate(R.id.action_nav_favorPublishedView_to_chatView);
          break;
        case R.id.commit_complete_button:
          if (currentFavor.getStatusId() == FavorStatus.REQUESTED.toInt()) commitFavor();
          else completeFavor();
          break;
        case R.id.user_profile_picture:
        case R.id.user_name_published_view:
          tryMoveToUserInfoPage(currentFavor.getRequesterId());
          break;
        case R.id.location:
          Bundle arguments = new Bundle();
          arguments.putInt(MapPage.LOCATION_ARGUMENT_KEY, MapPage.OBSERVE_FAVOR);
          arguments.putString(
              MapPage.LATITUDE_ARGUMENT_KEY,
              String.valueOf(currentFavor.getLocation().getLatitude()));
          arguments.putString(
              MapPage.LONGITUDE_ARGUMENT_KEY,
              String.valueOf(currentFavor.getLocation().getLongitude()));
          findNavController(requireActivity(), R.id.nav_host_fragment)
              .navigate(R.id.action_global_nav_map, arguments);
          break;
      }
    }
  }

  private void tryMoveToUserInfoPage(String userId) {

    // check if user exists
    DependencyFactory.getCurrentUserRepository()
        .findUser(userId)
        .thenAccept(
            user -> {
              Bundle userBundle = new Bundle();
              userBundle.putString(CommonTools.USER_ARGS, userId);
              findNavController(requireView())
                  .navigate(R.id.action_nav_favorPublishedView_to_UserInfoPage, userBundle);
            })
        .exceptionally(
            t -> {
              CommonTools.showSnackbar(getView(), getString(R.string.user_not_present_message));
              return null;
            });
  }

  private void displayFromFavor(View rootView, Favor favor) {

    String timeStr =
        getString(R.string.posted_placeholder, CommonTools.convertTime(favor.getPostedTime()));
    String titleStr = favor.getTitle();
    String descriptionStr = favor.getDescription();
    String favoCoinStr = getString(R.string.favor_worth, favor.getReward());
    setupTextView(rootView, R.id.time, timeStr);
    setupTextView(rootView, R.id.title, titleStr);
    setupTextView(rootView, R.id.value, favoCoinStr);
    if (descriptionStr == null || descriptionStr.equals(""))
      rootView.findViewById(R.id.description).setVisibility(View.GONE);
    else setupTextView(rootView, R.id.description, descriptionStr);

    isRequestedByCurrentUser = favor.getRequesterId().equals(currentUserId);
    favorStatus = verifyFavorHasBeenAccepted(favor);

    // display committed user list
    if (isRequestedByCurrentUser) setupUserListView();
    else rootView.findViewById(R.id.commit_user_group).setVisibility(View.INVISIBLE);
    setupImageView(rootView, favor);
    displayUserInfo(favor.getRequesterId());
    updateAppBarMenuDisplay();
    updateDisplayFromViewStatus();
  }

  private void setupTextView(View rootView, int id, String text) {
    TextView textView = rootView.findViewById(id);
    textView.setText(text);
    textView.setKeyListener(null);
  }

  private void displayUserInfo(String userId) {
    DependencyFactory.getCurrentUserRepository().findUser(userId).thenAccept(this::displayUserInfo);
  }

  private void displayUserInfo(User user) {
    String name =
        (user.getName() == null || user.getName().equals(""))
            ? CommonTools.emailToName(user.getEmail())
            : user.getName();
    ((TextView) requireView().findViewById(R.id.user_name_published_view)).setText(name);
    if (user.getProfilePictureUrl() != null) {
      Glide.with(this).load(user.getProfilePictureUrl()).fitCenter().into(userProfilePicture);
    }
  }

  private void setupImageView(View rootView, Favor favor) {
    String url = favor.getPictureUrl();
    if (url != null) {
      imageView = rootView.findViewById(R.id.picture);
      View loadingPanelView = rootView.findViewById(R.id.loading_panel);
      loadingPanelView.setVisibility(View.VISIBLE);
      getViewModel()
          .downloadPicture(favor)
          .thenAccept(
              picture -> {
                imageView.setImageBitmap(picture);
                loadingPanelView.setVisibility(View.GONE);
              });
    } else rootView.findViewById(R.id.picture).setVisibility(View.GONE);
  }

  private void setupUserListView() {
    requireView().findViewById(R.id.commit_user_group).setVisibility(View.VISIBLE);
    ListView listView = requireView().findViewById(R.id.commit_user);
    for (String userId : currentFavor.getUserIds()) {
      if (!userId.equals(currentFavor.getRequesterId()))
        DependencyFactory.getCurrentUserRepository()
            .findUser(userId)
            .thenAccept(
                user -> {
                  commitUsers.put(userId, user);
                  listView.setAdapter(
                      new UserAdapter(getContext(), new ArrayList<>(commitUsers.values())));
                  ViewGroup.LayoutParams params = listView.getLayoutParams();
                  params.height = (LIST_ITEM_HEIGHT) * (commitUsers.size());
                  listView.setLayoutParams(params);
                });
    }
    listView.setOnItemClickListener(
        (parent, view, position, id) -> {
          User user = (User) parent.getItemAtPosition(position);
          PopupMenu popup = new PopupMenu(requireActivity(), view);
          popup.getMenuInflater().inflate(R.menu.user_popup_menu, popup.getMenu());
          if (currentFavor.getStatusId() != FavorStatus.REQUESTED.toInt())
            popup.getMenu().findItem(R.id.accept_popup).setVisible(false);
          popup.setOnMenuItemClickListener(
              item -> {
                if (item.getItemId() == R.id.accept_popup) {
                  acceptFavor(user);
                } else if (item.getItemId() == R.id.profile_popup) {
                  tryMoveToUserInfoPage(user.getId());
                }
                return false;
              });
          popup.show();
        });
  }

  private void updateDisplayFromViewStatus() {
    toolbar.setTitle(favorStatus.toString());
    toolbar.setBackgroundColor(getResources().getColor(FavorStatus.statusColor.get(favorStatus)));
    switch (favorStatus) {
      case ACCEPTED:
        updateCompleteBtnDisplay(R.string.complete_favor, true, R.drawable.ic_check_box_black_24dp);
        break;
      case REQUESTED:
        if (isRequestedByCurrentUser || isPotentialHelper())
          updateCompleteBtnDisplay(R.string.commit_favor, false, R.drawable.ic_thumb_up_24dp);
        else updateCompleteBtnDisplay(R.string.commit_favor, true, R.drawable.ic_thumb_up_24dp);
        break;
      case COMPLETED_ACCEPTER:
        updateCompleteBtnDisplay(
            R.string.complete_favor, isRequestedByCurrentUser, R.drawable.ic_check_box_black_24dp);
        break;
      case COMPLETED_REQUESTER:
        updateCompleteBtnDisplay(
            R.string.complete_favor, !isRequestedByCurrentUser, R.drawable.ic_check_box_black_24dp);
        break;
      default: // archived and include accepted by other
        showBottomBar(false);
    }
  }

  private void updateCompleteBtnDisplay(int txt, boolean visible, int icon) {
    commitAndCompleteBtn.setText(txt);
    commitAndCompleteBtn.setVisibility(visible ? View.VISIBLE : View.INVISIBLE);
    commitAndCompleteBtn.setCompoundDrawablesWithIntrinsicBounds(icon, 0, 0, 0);
  }

  private boolean isPotentialHelper() {
    return currentFavor.getUserIds().contains(currentUserId) && (!isRequestedByCurrentUser);
  }

  private void showBottomBar(boolean visible) {
    requireView().findViewById(R.id.buttons_bar).setVisibility(visible ? View.VISIBLE : View.GONE);
  }

  // Verifies favor hasn't already been accepted
  private FavorStatus verifyFavorHasBeenAccepted(Favor favor) {
    FavorStatus favorStatus = FavorStatus.toEnum(favor.getStatusId());
    if (favor.getAccepterId() != null && !favor.getRequesterId().equals(currentUserId)) {
      if (favorStatus.equals(FavorStatus.ACCEPTED)
          && !favor.getAccepterId().equals(currentUserId)) {
        favorStatus = FavorStatus.ACCEPTED_BY_OTHER;
      }
    }
    return favorStatus;
  }

  private void goEditFavor() {
    currentFavor.setStatusIdToInt(FavorStatus.EDIT);
    Bundle favorBundle = new Bundle();
    favorBundle.putParcelable(CommonTools.FAVOR_VALUE_ARGS, currentFavor);
    favorBundle.putString(
        FavorEditingView.FAVOR_SOURCE_KEY, FavorEditingView.FAVOR_SOURCE_PUBLISHED_EDIT);
    findNavController(requireActivity(), R.id.nav_host_fragment)
        .navigate(R.id.action_global_favorEditingView, favorBundle);
  }

  private void reuseFavor() {
    Bundle favorBundle = new Bundle();
    Favor newFavor =
        new Favor(
            currentFavor.getTitle(),
            currentFavor.getDescription(),
            currentFavor.getRequesterId(),
            currentFavor.getLocation(),
            FavorStatus.EDIT.toInt(),
            currentFavor.getReward(),
            currentFavor.getPictureUrl());
    favorBundle.putParcelable(CommonTools.FAVOR_VALUE_ARGS, newFavor);
    favorBundle.putString(
        FavorEditingView.FAVOR_SOURCE_KEY, FavorEditingView.FAVOR_SOURCE_PUBLISHED_REUSE);
    findNavController(requireActivity(), R.id.nav_host_fragment)
        .navigate(R.id.action_global_favorEditingView, favorBundle);
  }

  private void commitFavor() {
    if (favorViewModel.getActiveAcceptedFavors() >= User.MAX_ACCEPTING_FAVORS) {
      CommonTools.showSnackbar(requireView(), getString(R.string.illegal_accept_error));
      return;
    }
    CompletableFuture<Void> commitFuture = getViewModel().commitFavor(currentFavor, false);
    handleResult(commitFuture, R.string.favor_respond_success_msg);
  }

  private void cancelCommit() {
    CompletableFuture<Void> commitFuture = getViewModel().commitFavor(currentFavor, true);
    handleResult(commitFuture, R.string.favor_respond_success_msg);
  }

  private void handleResult(CompletableFuture<Void> commitFuture, int successMessage) {
    commitFuture.whenComplete(
        (aVoid, throwable) -> {
          if (throwable != null)
            CommonTools.showSnackbar(requireView(), getString(R.string.update_favor_error));
          else CommonTools.showSnackbar(requireView(), getString(successMessage));
        });
  }

  private void acceptFavor(User user) {
    CompletableFuture<Void> acceptFavorFuture = getViewModel().acceptFavor(currentFavor, user);
    handleResult(acceptFavorFuture, R.string.favor_respond_success_msg);
  }

  private void completeFavor() {
    CompletableFuture<Void> completeFuture =
        getViewModel().completeFavor(currentFavor, isRequestedByCurrentUser);
    handleResult(completeFuture, R.string.favor_complete_success_msg);

    // review favor
    completeFuture.thenAccept((aVoid) -> reviewFavorExperience());
  }

  private void reviewFavorExperience() {
    String otherUserId;
    if (currentFavor.getRequesterId().equals(DependencyFactory.getCurrentFirebaseUser().getUid())) {
      otherUserId = currentFavor.getAccepterId();
    } else {
      otherUserId = currentFavor.getRequesterId();
    }
    IUserUtil userRepository = DependencyFactory.getCurrentUserRepository();

    new AlertDialog.Builder(requireActivity())
        .setMessage(getText(R.string.feedback_description))
        .setPositiveButton(
            getText(R.string.positive_feedback),
            (dialogInterface, i) -> {
              CompletableFuture<Void> feedbackFuture =
                  userRepository.incrementFieldForUser(otherUserId, User.LIKES, 1);
              handleResult(feedbackFuture, R.string.feedback_message);
            })
        .setNegativeButton(
            getText(R.string.negative_feedback),
            (dialogInterface, i) -> {
              CompletableFuture<Void> feedbackFuture =
                  userRepository.incrementFieldForUser(otherUserId, User.DISLIKES, 1);
              handleResult(feedbackFuture, R.string.feedback_message);
            })
        .show();
  }

  private void cancelFavor() {
    // remove the potential helpers before cancel
    if (favorStatus == FavorStatus.REQUESTED)
      for (int i = 1; i < currentFavor.getUserIds().size(); i++)
        currentFavor.getUserIds().remove(i);
    CompletableFuture<Void> cancelFuture =
        getViewModel().cancelFavor(currentFavor, isRequestedByCurrentUser);
    handleResult(cancelFuture, R.string.favor_cancel_success_msg);
  }

  private void deleteFavor() {
    CompletableFuture<Void> deleteFuture = getViewModel().deleteFavor(currentFavor);
    handleResult(deleteFuture, R.string.favor_delete_success_msg);
    deleteFuture.thenAccept(aVoid -> requireActivity().onBackPressed());
  }
}
