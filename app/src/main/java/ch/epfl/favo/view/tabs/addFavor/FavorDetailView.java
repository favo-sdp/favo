package ch.epfl.favo.view.tabs.addFavor;

import android.annotation.SuppressLint;
import android.graphics.drawable.Drawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.fragment.app.Fragment;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.function.Consumer;
import java.util.function.Function;

import ch.epfl.favo.R;
import ch.epfl.favo.exception.IllegalRequestException;
import ch.epfl.favo.favor.Favor;
import ch.epfl.favo.favor.FavorStatus;
import ch.epfl.favo.user.UserUtil;
import ch.epfl.favo.util.CommonTools;
import ch.epfl.favo.util.DependencyFactory;
import ch.epfl.favo.view.NonClickableToolbar;
import ch.epfl.favo.viewmodel.IFavorViewModel;

import static androidx.navigation.Navigation.findNavController;

@SuppressLint("NewApi")
public class FavorDetailView extends Fragment {

  private FavorStatus favorStatus;
  private Favor currentFavor;
  private Button locationAccessBtn;
  private Button acceptAndCancelFavorBtn;
  private Button chatBtn;
  private Button completeBtn;
  private IFavorViewModel favorViewModel;
  private NonClickableToolbar toolbar;

  public FavorDetailView() {
    // create favor detail from a favor
  }

  @Override
  public View onCreateView(
      LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
    // inflate view
    View rootView = inflater.inflate(R.layout.fragment_favor_accept_view, container, false);
    setupButtons(rootView);

    toolbar = requireActivity().findViewById(R.id.toolbar_main_activity);
    favorViewModel =
        (IFavorViewModel)
            new ViewModelProvider(requireActivity())
                .get(DependencyFactory.getCurrentViewModelClass());
    String favorId = "";
    if (currentFavor != null) favorId = currentFavor.getId();
    if (getArguments() != null) favorId = getArguments().getString(CommonTools.FAVOR_ARGS);
    setupFavorListener(rootView, favorId);
    return rootView;
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
                enableButtons(false);
              }
            });
  }

  private void setupButtons(View rootView) {
    acceptAndCancelFavorBtn = rootView.findViewById(R.id.accept_button);
    locationAccessBtn = rootView.findViewById(R.id.location_accept_view_btn);
    completeBtn = rootView.findViewById(R.id.complete_btn);
    completeBtn.setOnClickListener(v -> completeFavor());
    chatBtn = rootView.findViewById(R.id.chat_button_accept_view);
    locationAccessBtn.setOnClickListener(
        v -> {
          favorViewModel.setShowObservedFavor(true);
          findNavController(requireActivity(), R.id.nav_host_fragment)
              .popBackStack(R.id.nav_map, false);
        });

    // If clicking for the first time, then accept the favor
    acceptAndCancelFavorBtn.setOnClickListener(
        v -> {
          if (currentFavor.getStatusId() == FavorStatus.REQUESTED.toInt()) acceptFavor();
          else cancelFavor();
        });

    chatBtn.setOnClickListener(
        v -> {
          Bundle favorBundle = new Bundle();
          favorBundle.putParcelable("FAVOR_ARGS", currentFavor);
          Navigation.findNavController(requireView())
              .navigate(R.id.action_nav_favorDetailView_to_chatView, favorBundle);
        });

    rootView
        .findViewById(R.id.requester_name)
        .setOnClickListener(
            v -> {
              Bundle userBundle = new Bundle();
              userBundle.putString("USER_ARGS", currentFavor.getRequesterId());
              Navigation.findNavController(requireView())
                  .navigate(R.id.action_nav_favorDetailView_to_UserInfoPage, userBundle);
            });
  }

  private void completeFavor() {
    CompletableFuture updateFuture = getViewModel().completeFavor(currentFavor, false);
    updateFuture.thenAccept(
        o ->
            CommonTools.showSnackbar(
                requireView(), getString(R.string.favor_complete_success_msg)));
    updateFuture.exceptionally(handleException());
  }

  private void cancelFavor() {
    CompletableFuture completableFuture = getViewModel().cancelFavor(currentFavor, false);
    completableFuture.thenAccept(successfullyCancelledConsumer());
    completableFuture.exceptionally(handleException());
  }

  private Consumer successfullyCancelledConsumer() {
    return o -> {
      CommonTools.showSnackbar(getView(), getString(R.string.favor_cancel_success_msg));
    };
  }

  // Verifies favor hasn't already been accepted
  private FavorStatus verifyFavorHasBeenAccepted(Favor favor) {
    FavorStatus favorStatus = FavorStatus.toEnum(favor.getStatusId());
    if (favor.getAccepterId() != null) {
      if (favorStatus.equals(FavorStatus.ACCEPTED)
          && !favor.getAccepterId().equals(DependencyFactory.getCurrentFirebaseUser().getUid())) {
        favorStatus = FavorStatus.ACCEPTED_BY_OTHER;
      }
    }
    return favorStatus;
  }

  private void acceptFavor() {
    // TODO: we should prevent accepting favor by oneself, but with different way
    // this rare case only happens if I click the notification when someone accepts my favor, app
    // will open the detail view
    // of my requested favor, then I can accept my own favor.
    /* if (currentFavor.getRequesterId().equals(DependencyFactory.getCurrentFirebaseUser().getUid())) {
      CommonTools.showSnackbar(getView(), getString(R.string.favor_accept_by_oneself));
      return;
    }*/
    // update DB with accepted status
    CompletableFuture acceptFavorFuture = getViewModel().acceptFavor(currentFavor);
    acceptFavorFuture.thenAccept(
        o -> CommonTools.showSnackbar(getView(), getString(R.string.favor_respond_success_msg)));
    acceptFavorFuture.exceptionally(handleException());
  }

  private Function handleException() {
    return e -> {
      if (((CompletionException) e).getCause() instanceof IllegalRequestException)
        CommonTools.showSnackbar(requireView(), getString(R.string.illegal_accept_error));
      else CommonTools.showSnackbar(requireView(), getString(R.string.update_favor_error));
      return null;
    };
  }

  private void displayFromFavor(View rootView, Favor favor) {

    String timeStr = CommonTools.convertTime(favor.getLocation().getTime());
    String titleStr = favor.getTitle();
    String descriptionStr = favor.getDescription();
    // update status string
    favorStatus = verifyFavorHasBeenAccepted(favor);
    updateDisplayFromViewStatus();

    setupTextView(rootView, R.id.datetime_accept_view, timeStr);
    setupTextView(rootView, R.id.title_accept_view, titleStr);
    setupTextView(rootView, R.id.details_accept_view, descriptionStr);

    UserUtil.getSingleInstance()
        .findUser(favor.getRequesterId())
        .thenAccept(
            user ->
                ((TextView) rootView.findViewById(R.id.requester_name)).setText(user.getName()));
  }

  private void updateDisplayFromViewStatus() {
    updateAcceptBtnDisplay();
    toolbar.setTitle(favorStatus.toString());
    switch (favorStatus) {
      case SUCCESSFULLY_COMPLETED:
        {
          updateCompleteBtnDisplay(
              R.string.complete_favor, false, R.drawable.ic_check_box_black_24dp, 0);
          enableButtons(false);
          break;
        }
      case ACCEPTED:
        {
          updateCompleteBtnDisplay(
              R.string.complete_favor, true, R.drawable.ic_check_box_black_24dp, 1);
          toolbar.setBackgroundColor(getResources().getColor(R.color.accepted_status_bg));
          break;
        }
      case REQUESTED:
        {
          updateCompleteBtnDisplay(
              R.string.complete_favor, false, R.drawable.ic_check_box_black_24dp, 0);
          toolbar.setBackgroundColor(getResources().getColor(R.color.requested_status_bg));
          break;
        }
      case COMPLETED_ACCEPTER:
        {
          updateCompleteBtnDisplay(
              R.string.wait_complete, false, R.drawable.ic_watch_later_black_24dp, 1);
          break;
        }
      case COMPLETED_REQUESTER:
        {
          updateCompleteBtnDisplay(
              R.string.complete_favor, true, R.drawable.ic_check_box_black_24dp, 1);
          break;
        }
      default: // includes accepted by other
        enableButtons(false);
        updateCompleteBtnDisplay(
            R.string.complete_favor, false, R.drawable.ic_check_box_black_24dp, 0);
        toolbar.setBackgroundColor(getResources().getColor(R.color.cancelled_status_bg));
    }
  }

  private void updateCompleteBtnDisplay(int txt, boolean clickable, int icon, int weight) {
    completeBtn.setText(txt);
    completeBtn.setClickable(clickable);
    completeBtn.setCompoundDrawablesWithIntrinsicBounds(0, 0, icon, 0);
    ((LinearLayout.LayoutParams) completeBtn.getLayoutParams()).weight = weight;
  }

  private void updateAcceptBtnDisplay() {
    String displayMessage;
    int backgroundColor;
    Drawable img;
    if (favorStatus != FavorStatus.REQUESTED) {
      displayMessage = getString(R.string.cancel_accept_button_display);
      backgroundColor = R.color.fui_transparent;
      img = getResources().getDrawable(R.drawable.ic_cancel_24dp);
    } else { // includes ACCEPTED_BY_OTHER
      displayMessage = getResources().getString(R.string.accept_favor);
      img = getResources().getDrawable(R.drawable.ic_thumb_up_24dp);
      backgroundColor = android.R.drawable.btn_default;
    }
    acceptAndCancelFavorBtn.setText(displayMessage);
    acceptAndCancelFavorBtn.setBackgroundResource(backgroundColor);
    acceptAndCancelFavorBtn.setCompoundDrawablesWithIntrinsicBounds(null, null, null, img);
  }

  private void enableButtons(boolean enable) {
    acceptAndCancelFavorBtn.setEnabled(enable);
    chatBtn.setEnabled(enable);
    completeBtn.setEnabled(enable);
  }

  private void setupTextView(View rootView, int id, String text) {
    TextView textView = rootView.findViewById(id);
    textView.setText(text);
    textView.setKeyListener(null);
  }
}
