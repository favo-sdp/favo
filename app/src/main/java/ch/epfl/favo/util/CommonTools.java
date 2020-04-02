package ch.epfl.favo.util;

import android.app.Activity;
import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.google.android.material.snackbar.Snackbar;

import java.text.Format;
import java.text.SimpleDateFormat;
import java.util.Date;

public class CommonTools {
  public static void showSnackbar(View view, String errorMessageRes) {
    Snackbar.make(view, errorMessageRes, Snackbar.LENGTH_LONG).show();
  }

  public static void replaceFragment(
      int id, FragmentManager fragmentManager, Fragment newFragment) {
    FragmentTransaction transaction = fragmentManager.beginTransaction();
    transaction.replace(id, newFragment);
    transaction.addToBackStack(null);
    transaction.commit();
    // transaction.remove(this);
  }

  public static String convertTime(long time) {
    Date date = new Date(time);
    Format format = new SimpleDateFormat("yyyy MM dd HH:mm:ss");
    return format.format(date);
  }

  public static void hideKeyboardFrom(Context context, View view) {
    InputMethodManager imm = (InputMethodManager) context.getSystemService(Activity.INPUT_METHOD_SERVICE);
    imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
  }
}