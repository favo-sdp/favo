package ch.epfl.favo.view;

import android.util.Log;
import android.view.View;
import android.widget.EditText;

import androidx.test.espresso.UiController;
import androidx.test.espresso.ViewAction;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.uiautomator.UiDevice;

import com.google.android.gms.tasks.Tasks;

import org.hamcrest.Matcher;
import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.ExecutionException;

import ch.epfl.favo.FakeFirebaseUser;
import ch.epfl.favo.FakeItemFactory;
import ch.epfl.favo.MainActivity;
import ch.epfl.favo.R;
import ch.epfl.favo.TestConstants;
import ch.epfl.favo.favor.Favor;
import ch.epfl.favo.util.DependencyFactory;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.Espresso.pressBack;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.pressImeActionButton;
import static androidx.test.espresso.action.ViewActions.swipeDown;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayingAtLeast;
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
public class FavorPageTest {

  @Rule
  public final ActivityTestRule<MainActivity> mainActivityTestRule =
      new ActivityTestRule<MainActivity>(MainActivity.class) {
        @Override
        protected void beforeActivityLaunched() {
          DependencyFactory.setCurrentFirebaseUser(
              new FakeFirebaseUser(NAME, EMAIL, PHOTO_URI, PROVIDER));
          DependencyFactory.setCurrentGpsTracker(new MockGpsTracker());
        }
      };

  @Rule
  public GrantPermissionRule permissionRule =
      GrantPermissionRule.grant(android.Manifest.permission.ACCESS_FINE_LOCATION);

  @After
  public void tearDown() throws ExecutionException, InterruptedException {
    cleanupDatabase();
    DependencyFactory.setCurrentFirebaseUser(null);
    DependencyFactory.setCurrentGpsTracker(null);
  }

  private void cleanupDatabase() throws ExecutionException, InterruptedException {
    Tasks.await(
            DependencyFactory.getCurrentFirestore()
                .collection("favors")
                .whereArrayContains("userIds", TestConstants.USER_ID)
                .get())
        .getDocuments()
        .forEach(
            documentSnapshot ->
                documentSnapshot
                    .getReference()
                    .delete()
                    .addOnSuccessListener(
                        aVoid -> Log.d("FavorPageTests", "DocumentSnapshot successfully deleted!"))
                    .addOnFailureListener(
                        e -> {
                          Log.e("FavorPageTests", "Error deleting document", e);
                        }));
  }

  public static ViewAction withCustomConstraints(final ViewAction action, final Matcher<View> constraints) {
    return new ViewAction() {
      @Override
      public Matcher<View> getConstraints() {
        return constraints;
      }

      @Override
      public String getDescription() {
        return action.getDescription();
      }

      @Override
      public void perform(UiController uiController, View view) {
        action.perform(uiController, view);
      }
    };
  }

  @Test
  public void testFavorPageElements() {
    // click on favors tab
    onView(withId(R.id.nav_favorList)).check(matches(isDisplayed())).perform(click());
    getInstrumentation().waitForIdleSync();

    // check that tab 2 is indeed opened
    onView(allOf(withId(R.id.fragment_favors), withParent(withId(R.id.nav_host_fragment))))
        .check(matches(isDisplayed()));

    onView(withId(R.id.floatingActionButton)).check(matches(isDisplayed()));

    getInstrumentation().waitForIdleSync();

    onView(withText(R.string.favor_no_active_favor)).check(matches(isDisplayed()));

    onView(withId(R.id.swipe_refresh_layout))
            .perform(withCustomConstraints(swipeDown(), isDisplayingAtLeast(85)));

    onView(withId(R.id.archived_toggle)).check(matches(isDisplayed())).perform(click());

    getInstrumentation().waitForIdleSync();

    onView(withText(R.string.favor_no_archived_favor)).check(matches(isDisplayed()));

    onView(withId(R.id.swipe_refresh_layout))
            .perform(withCustomConstraints(swipeDown(), isDisplayingAtLeast(85)));

    onView(withId(R.id.active_toggle)).perform(click());
  }

  @Test
  public void testFavorRequestUpdatesListView() {
    // Click on favors tab
    onView(withId(R.id.nav_favorList)).check(matches(isDisplayed())).perform(click());
    getInstrumentation().waitForIdleSync();

    // Click on new favor tab
    onView(withId(R.id.floatingActionButton)).check(matches(isDisplayed())).perform(click());
    getInstrumentation().waitForIdleSync();

    // Fill in text views with fake favor
    Favor favor = FakeItemFactory.getFavor();

    onView(withId(R.id.title_request_view)).perform(typeText(favor.getTitle()));
    onView(withId(R.id.details)).perform(typeText(favor.getDescription()));

    // Click on request button
    onView(withId(R.id.request_button)).check(matches(isDisplayed())).perform(click());
    getInstrumentation().waitForIdleSync();

    // Click on back button
    pressBack();
    getInstrumentation().waitForIdleSync();
  }

  @Test
  public void testFavorCancelUpdatesActiveAndArchivedListView() throws InterruptedException {
    // Click on favors tab
    onView(withId(R.id.nav_favorList)).check(matches(isDisplayed())).perform(click());
    getInstrumentation().waitForIdleSync();

    // Click on new favor tab
    onView(withId(R.id.floatingActionButton)).check(matches(isDisplayed())).perform(click());
    getInstrumentation().waitForIdleSync();

    // Fill in text views with fake favor
    Favor favor = FakeItemFactory.getFavor();

    onView(withId(R.id.title_request_view)).perform(typeText(favor.getTitle()));
    onView(withId(R.id.details)).perform(typeText(favor.getDescription()));

    // Click on request button
    onView(withId(R.id.request_button)).check(matches(isDisplayed())).perform(click());
    getInstrumentation().waitForIdleSync();
    Thread.sleep(4000); // wait for snackbar to hide

    // Click on cancel button
    onView(withId(R.id.cancel_favor_button)).check(matches(isDisplayed())).perform(click());
    getInstrumentation().waitForIdleSync();

    // Go back
    pressBack();
    getInstrumentation().waitForIdleSync();
  }

  private void requestFavorAndSearch() {
    // Click on favors tab
    onView(withId(R.id.nav_favorList)).check(matches(isDisplayed())).perform(click());
    getInstrumentation().waitForIdleSync();

    // Click on new favor tab
    onView(withId(R.id.floatingActionButton)).check(matches(isDisplayed())).perform(click());
    getInstrumentation().waitForIdleSync();

    // Fill in text views with fake favor
    Favor favor = FakeItemFactory.getFavor();

    onView(withId(R.id.title_request_view)).perform(typeText(favor.getTitle()));
    onView(withId(R.id.details)).perform(typeText(favor.getDescription()));

    // Click on request button
    onView(withId(R.id.request_button)).check(matches(isDisplayed())).perform(click());
    getInstrumentation().waitForIdleSync();

    // Click on back button
    pressBack();
    getInstrumentation().waitForIdleSync();

    // Click on searchView button
    onView(withId(R.id.search_item)).check(matches(isDisplayed())).perform(click());
    getInstrumentation().waitForIdleSync();
  }

  @Test
  public void testSearchViewFound() {

    requestFavorAndSearch();

    Favor favor = FakeItemFactory.getFavor();

    onView(isAssignableFrom(EditText.class)).perform(typeText(favor.getTitle()));

    getInstrumentation().waitForIdleSync();
  }

  @Test
  public void testSearchViewNotFound() {

    requestFavorAndSearch();

    // type the title of fake favor
    onView(isAssignableFrom(EditText.class))
        .perform(typeText("random words"), pressImeActionButton());

    // check the tip text is displayed when query failed
    onView(withId(R.id.tip))
        .check(matches(isDisplayed()))
        .check(matches(withText(R.string.query_failed)));
  }

  @Test
  public void testClickScreenHideKeyboard() {
    requestFavorAndSearch();

    // Click on upper left screen corner
    UiDevice device = UiDevice.getInstance(getInstrumentation());
    device.click(device.getDisplayWidth() / 2, device.getDisplayHeight() / 2);

    // if keyboard hidden, one time of pressBack will return to Favor List view
    onView(withId(R.id.hamburger_menu_button)).check(matches(isDisplayed())).perform(click());
  }
}
