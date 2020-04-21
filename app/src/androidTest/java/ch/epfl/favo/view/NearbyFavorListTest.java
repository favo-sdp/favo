package ch.epfl.favo.view;

import android.util.Log;
import android.view.KeyEvent;
import android.widget.EditText;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;
import androidx.test.rule.GrantPermissionRule;
import androidx.test.uiautomator.UiDevice;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import ch.epfl.favo.FakeFirebaseUser;
import ch.epfl.favo.FakeItemFactory;
import ch.epfl.favo.MainActivity;
import ch.epfl.favo.R;
import ch.epfl.favo.favor.Favor;
import ch.epfl.favo.favor.FavorUtil;
import ch.epfl.favo.util.DependencyFactory;

import static androidx.test.espresso.Espresso.onView;
import static androidx.test.espresso.action.ViewActions.click;
import static androidx.test.espresso.action.ViewActions.pressKey;
import static androidx.test.espresso.action.ViewActions.typeText;
import static androidx.test.espresso.assertion.ViewAssertions.doesNotExist;
import static androidx.test.espresso.assertion.ViewAssertions.matches;
import static androidx.test.espresso.matcher.ViewMatchers.isAssignableFrom;
import static androidx.test.espresso.matcher.ViewMatchers.isDisplayed;
import static androidx.test.espresso.matcher.ViewMatchers.withId;
import static androidx.test.espresso.matcher.ViewMatchers.withText;
import static androidx.test.platform.app.InstrumentationRegistry.getInstrumentation;
import static ch.epfl.favo.TestConstants.EMAIL;
import static ch.epfl.favo.TestConstants.NAME;
import static ch.epfl.favo.TestConstants.PHOTO_URI;
import static ch.epfl.favo.TestConstants.PROVIDER;

@RunWith(AndroidJUnit4.class)
public class NearbyFavorListTest {
    MockDatabaseWrapper databaseWrapper;
    private Favor favor = FakeItemFactory.getFavor();
    @Rule
    public final ActivityTestRule<MainActivity> mainActivityTestRule =
            new ActivityTestRule<MainActivity>(MainActivity.class) {
                @Override
                protected void beforeActivityLaunched() {
                    DependencyFactory.setCurrentFirebaseUser(
                            new FakeFirebaseUser(NAME, EMAIL, PHOTO_URI, PROVIDER));
                    // setup mock gps
                    DependencyFactory.setCurrentGpsTracker(new MockGpsTracker());
                    // setup mock db
                    databaseWrapper = new MockDatabaseWrapper();
                    databaseWrapper.setThrowError(false);
                    DependencyFactory.setCurrentCollectionWrapper(databaseWrapper);
                }
            };
    @Rule
    public GrantPermissionRule permissionRule =
            GrantPermissionRule.grant(android.Manifest.permission.ACCESS_FINE_LOCATION);


    @After
    public void tearDown() {
        DependencyFactory.setCurrentCollectionWrapper(null);
        DependencyFactory.setCurrentFirebaseUser(null);
        DependencyFactory.setCurrentGpsTracker(null);
    }

    private void openSearchView(){
        try{
            databaseWrapper.setThrowError(false);
            FavorUtil.getSingleInstance().updateCollectionWrapper(databaseWrapper);
            // switch to nearby favor list view
            onView(withId(R.id.list_switch)).check(matches(isDisplayed())).perform(click());
            getInstrumentation().waitForIdleSync();

            onView(withId(R.id.map_switch)).check(matches(isDisplayed())).perform(click());
            getInstrumentation().waitForIdleSync();
            onView(withId(R.id.list_switch)).check(matches(isDisplayed())).perform(click());
            getInstrumentation().waitForIdleSync();

            //Click on searchView button
            onView(withId(R.id.search_item)).check(matches(isDisplayed())).perform(click());
            getInstrumentation().waitForIdleSync();
        }catch (Exception e){
            Log.d("listTest", e.getMessage());
        }
    }

    @Test
    public void testSearchViewFound() {
        databaseWrapper.setMockDocument(favor);
        databaseWrapper.setThrowError(false);
        FavorUtil.getSingleInstance().updateCollectionWrapper(databaseWrapper);
        openSearchView();

        //type the title of fake favor
        onView(isAssignableFrom(EditText.class)).perform(typeText(favor.getTitle())).perform(pressKey(KeyEvent.KEYCODE_ENTER));
        getInstrumentation().waitForIdleSync();

        // check query is successful and click on found item
        onView(withText(favor.getDescription())).check(matches(isDisplayed())).perform(click());
        getInstrumentation().waitForIdleSync();

        // Click on back button
        onView(withId(R.id.hamburger_menu_button)).check(matches(isDisplayed())).perform(click());
        getInstrumentation().waitForIdleSync();

        // check active favors are displayed in active favor list view
        onView(withText(favor.getDescription())).check(matches(isDisplayed()));
        getInstrumentation().waitForIdleSync();
    }

    @Test
    public void testSearchViewNotFound() {
        openSearchView();

        //type the title of fake favor
        onView(isAssignableFrom(EditText.class)).perform(typeText("random words")).perform(pressKey(KeyEvent.KEYCODE_ENTER));
        getInstrumentation().waitForIdleSync();

        // check the tip text is displayed when query failed
        onView(withId(R.id.nearby_tip))
                .check(matches(isDisplayed()))
                .check(matches(withText(R.string.query_failed)));
        onView(withText(favor.getDescription())).check(doesNotExist());

        // Click on back button
        onView(withId(R.id.hamburger_menu_button)).check(matches(isDisplayed())).perform(click());
        getInstrumentation().waitForIdleSync();

        // check active favors are displayed in active favor list view
        onView(withText(favor.getDescription())).check(matches(isDisplayed()));
        getInstrumentation().waitForIdleSync();
    }

    @Test
    public void testClickScreenHideKeyboard(){
        openSearchView();
        //Click on searchView button
        onView(isAssignableFrom(EditText.class)).check(matches(isDisplayed())).perform(click());
        getInstrumentation().waitForIdleSync();

        //Click on upper left screen corner
        UiDevice device = UiDevice.getInstance(getInstrumentation());
        device.click(device.getDisplayWidth() / 2, device.getDisplayHeight() / 2);

        // if keyboard is not displayed, one time of pressBack will return to Favor List view
        onView(withId(R.id.hamburger_menu_button)).check(matches(isDisplayed())).perform(click());
        // check favor is displayed in active favor list view
        onView(withText(favor.getTitle())).check(matches(isDisplayed()));
    }

    @Test
    public void FavorDetailViewJumptoMapTest() throws InterruptedException {
        Thread.sleep(1000);
        // switch to nearby favor list view
        onView(withId(R.id.list_switch)).check(matches(isDisplayed())).perform(click());
        getInstrumentation().waitForIdleSync();

        // check test favor is found click on found item
        onView(withText(favor.getDescription())).check(matches(isDisplayed())).perform(click());
        getInstrumentation().waitForIdleSync();

        // Check and click on the location text
        onView(withId(R.id.location_accept_view_btn)).check(matches(isDisplayed())).perform(click());
        onView(withId(R.id.fragment_map)).check(matches(isDisplayed()));
    }
}
