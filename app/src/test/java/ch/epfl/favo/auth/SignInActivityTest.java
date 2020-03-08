package ch.epfl.favo.auth;

import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;

import static android.app.Activity.RESULT_OK;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.spy;

public class SignInActivityTest {

  // These tests just want to make sure that no exception is thrown when
  // the result action of the sign-in is handled

  private SignInActivity spy;

  @Before
  public void setup() {
    spy = spy(SignInActivity.class);
  }

  @Test
  public void testOnActivityResult_requestCodeCorrect() {
    Mockito.doNothing().when(spy).showSnackbar(anyInt());
    spy.onActivityResult(123, 3, null);
  }

  @Test
  public void testOnActivityResult_requestCodeNotCorrect() {
    spy.onActivityResult(4, RESULT_OK, null);
  }

  @Test
  public void testOnActivityResult_resultNotOk() {
    Mockito.doNothing().when(spy).showSnackbar(anyInt());
    spy.onActivityResult(123, 10, null);
  }

  @Test
  public void testOnActivityResult_resultOk() {
    spy.onActivityResult(123, RESULT_OK, null);
  }
}
