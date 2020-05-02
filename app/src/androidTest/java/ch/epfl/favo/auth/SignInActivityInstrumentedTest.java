package ch.epfl.favo.auth;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.rule.ActivityTestRule;

import org.junit.After;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.concurrent.ExecutionException;

import ch.epfl.favo.FakeFirebaseUser;
import ch.epfl.favo.FakeUserUtil;
import ch.epfl.favo.util.DependencyFactory;
import ch.epfl.favo.view.MockGpsTracker;

import static android.app.Activity.RESULT_OK;
import static androidx.test.internal.runner.junit4.statement.UiThreadStatement.runOnUiThread;
import static ch.epfl.favo.TestConstants.EMAIL;
import static ch.epfl.favo.TestConstants.NAME;
import static ch.epfl.favo.TestConstants.PHOTO_URI;
import static ch.epfl.favo.TestConstants.PROVIDER;

@RunWith(AndroidJUnit4.class)
public class SignInActivityInstrumentedTest {
  private FakeUserUtil fakeUserUtil = new FakeUserUtil();

  @Rule
  public final ActivityTestRule<SignInActivity> activityTestRule =
      new ActivityTestRule<SignInActivity>(SignInActivity.class) {
        @Override
        protected void beforeActivityLaunched() {
          if( new FakeFirebaseUser(NAME, EMAIL, PHOTO_URI, PROVIDER) == null)
            throw new RuntimeException("56fsd");
                        DependencyFactory.setCurrentFirebaseUser(
                                new FakeFirebaseUser(NAME, EMAIL, PHOTO_URI, PROVIDER));
          DependencyFactory.setCurrentGpsTracker(new MockGpsTracker());
          DependencyFactory.setCurrentUserRepository(fakeUserUtil);
        }
      };

  @After
  public void tearDown() throws ExecutionException, InterruptedException {
    DependencyFactory.setCurrentGpsTracker(null);
    DependencyFactory.setCurrentFirebaseUser(null);
    DependencyFactory.setCurrentUserRepository(null);
  }

  @Test
  public void testSignInFlow() throws Throwable {
    //DependencyFactory.setCurrentFirebaseUser(new FakeFirebaseUser(NAME, EMAIL, PHOTO_URI, PROVIDER));
    handleSignInResponse(RESULT_OK);
  }

  @Test
  public void testSignInFlowWhenUserNotFound() throws Throwable {
    DependencyFactory.setCurrentFirebaseUser(new FakeFirebaseUser(NAME, EMAIL, PHOTO_URI, PROVIDER));
    fakeUserUtil.setFindUserFail(true);
    DependencyFactory.setCurrentUserRepository(fakeUserUtil);
    handleSignInResponse(RESULT_OK);
  }

  @Test
  public void testSnackBarShowsWhenNewUserFailsToBePosted() throws Throwable {
    DependencyFactory.setCurrentFirebaseUser(new FakeFirebaseUser(NAME, EMAIL, PHOTO_URI, PROVIDER));
    fakeUserUtil.setThrowResult(new RuntimeException());
    DependencyFactory.setCurrentUserRepository(fakeUserUtil);
    handleSignInResponse(RESULT_OK);
    // check fail snackbar shows TODO: Figure out how to show snackbar
    //    onView(withId(com.google.android.material.R.id.snackbar_text))
    //            .check(matches(withText(R.string.fui_error_unknown)));
  }

  private void handleSignInResponse(int result) throws Throwable {
    runOnUiThread(
        () ->
            activityTestRule
                .getActivity()
                .onActivityResult(SignInActivity.RC_SIGN_IN, result, null));
  }
}