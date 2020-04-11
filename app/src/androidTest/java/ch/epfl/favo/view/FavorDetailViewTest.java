package ch.epfl.favo.view;

import android.os.Bundle;

import androidx.navigation.NavController;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import androidx.test.rule.GrantPermissionRule;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.CompletableFuture;

import ch.epfl.favo.FakeFirebaseUser;
import ch.epfl.favo.FakeItemFactory;
import ch.epfl.favo.MainActivity;
import ch.epfl.favo.R;
import ch.epfl.favo.favor.Favor;
import ch.epfl.favo.user.UserUtil;
import ch.epfl.favo.util.DependencyFactory;
import ch.epfl.favo.util.FavorFragmentFactory;

import static androidx.navigation.Navigation.findNavController;
import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withParent;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static ch.epfl.favo.TestConstants.EMAIL;
import static ch.epfl.favo.TestConstants.NAME;
import static ch.epfl.favo.TestConstants.PHOTO_URI;
import static ch.epfl.favo.TestConstants.PROVIDER;
import static org.hamcrest.core.AllOf.allOf;

@RunWith(AndroidJUnit4.class)
public class FavorDetailViewTest {
  private Favor fakeFavor;
  private MockDatabaseWrapper mockDatabaseWrapper = new MockDatabaseWrapper<Favor>();

  @Rule
  public final ActivityTestRule<MainActivity> mainActivityTestRule =
      new ActivityTestRule<MainActivity>(MainActivity.class) {
        @Override
        protected void beforeActivityLaunched() {
          DependencyFactory.setCurrentFirebaseUser(
              new FakeFirebaseUser(NAME, EMAIL, PHOTO_URI, PROVIDER));
          DependencyFactory.setCurrentCollectionWrapper(mockDatabaseWrapper);
        }
      };

  @Rule
  public GrantPermissionRule permissionRule =
      GrantPermissionRule.grant(android.Manifest.permission.ACCESS_FINE_LOCATION);

  @Before
  public void setUp() {
    fakeFavor = FakeItemFactory.getFavor();
    UserUtil.currentUserId = "USER";
    NavController navController =
        findNavController(mainActivityTestRule.getActivity(), R.id.nav_host_fragment);
    Bundle bundle = new Bundle();
    bundle.putParcelable(FavorFragmentFactory.FAVOR_ARGS, fakeFavor);
    navController.navigate(R.id.action_global_favorDetailView, bundle);
  }

  @After
  public void tearDown() {
    DependencyFactory.setCurrentFirebaseUser(null);
    DependencyFactory.setCurrentCollectionWrapper(null);
  }

  @Test
  public void favorDetailViewIsLaunched() {
    // check that detailed view is indeed opened
    onView(
            allOf(
                withId(R.id.fragment_favor_accept_view),
                withParent(withId(R.id.nav_host_fragment))))
        .check(matches(isDisplayed()));
  }

  @Test
  public void testAcceptButtonShowsSnackBarAndUpdatesDisplay() {
    CompletableFuture successfulResult = new CompletableFuture();
    successfulResult.complete(null);
    mockDatabaseWrapper.setMockDocument(fakeFavor); // set favor in db
    mockDatabaseWrapper.setMockResult(successfulResult);
    onView(withId(R.id.accept_button)).perform(click());
    getInstrumentation().waitForIdleSync();
    // check snackbar shows
    onView(withId(com.google.android.material.R.id.snackbar_text))
        .check(matches(withText(R.string.favor_respond_success_msg)));
    onView(withId(R.id.status_text_accept_view))
        .check(matches(withText(Favor.Status.ACCEPTED.getPrettyString())));
  }

  @Test
  public void testAcceptButtonShowsFailSnackBar() {
    CompletableFuture failedResult = new CompletableFuture();
    failedResult.completeExceptionally(new RuntimeException());
    mockDatabaseWrapper.setMockResult(failedResult);
    onView(withId(R.id.accept_button)).perform(click());
    getInstrumentation().waitForIdleSync();
    // check snackbar shows
    onView(withId(com.google.android.material.R.id.snackbar_text))
        .check(matches(withText("Failed to update")));
  }
  @Test
  public void testFavorFailsToBeAcceptedIfPreviouslyAccepted(){
    fakeFavor.setStatusId(Favor.Status.ACCEPTED);
    mockDatabaseWrapper.setMockDocument(fakeFavor);
    onView(withId(R.id.accept_button)).perform(click());
    getInstrumentation().waitForIdleSync();
    // check snackbar shows
    onView(withId(com.google.android.material.R.id.snackbar_text))
            .check(matches(withText("Failed to update")));

  }
}
