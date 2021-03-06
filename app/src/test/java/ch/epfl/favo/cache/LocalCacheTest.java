package ch.epfl.favo.cache;

import android.content.Context;
import android.content.SharedPreferences;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.Mock;
import org.mockito.Mockito;
import org.mockito.junit.MockitoJUnitRunner;

import java.io.File;

import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@RunWith(MockitoJUnitRunner.Silent.class)
public class LocalCacheTest {

  @Mock Context mockContext;
  @Mock SharedPreferences mockSharedPreferences;
  @Mock SharedPreferences.Editor mockEditor;

  private static String FAVORID = "favorID";

  @Before
  public void before() {
    //
    // Mockito.when(PreferenceManager.getDefaultSharedPreferences(mockContext)).thenReturn(mockSharedPreferences);
    Mockito.when(mockContext.getSharedPreferences(anyString(), anyInt()))
        .thenReturn(mockSharedPreferences);
    Mockito.when(mockSharedPreferences.edit()).thenReturn(mockEditor);
    Mockito.when(mockEditor.putString(anyString(), anyString())).thenReturn(mockEditor);
    Mockito.when(mockEditor.putBoolean(anyString(), anyBoolean())).thenReturn(mockEditor);
    Mockito.when(mockSharedPreferences.getString(anyString(), anyString())).thenReturn("");
    Mockito.when(mockSharedPreferences.getBoolean(anyString(), anyBoolean())).thenReturn(false);

    File temp = new File("data/user/0/ch.epfl.favo/files");
    Mockito.when(mockContext.getFilesDir()).thenReturn(temp);
  }

  @Test
  public void test_storeKeyValueStr() {
    CacheUtil.getInstance().storeKeyValueStr(mockContext, "key", "value");
    verify(mockEditor, times(1)).putString("key", "value");
    verify(mockEditor, times(1)).apply();
  }

  @Test
  public void test_storeKeyValueBool() {
    CacheUtil.getInstance().storeKeyValueBool(mockContext, "key2", true);
    verify(mockEditor, times(1)).putBoolean("key2", true);
    verify(mockEditor, times(1)).apply();
  }

  @Test
  public void test_getValueFromCacheStr() {
    CacheUtil.getInstance().getValueFromCacheStr(mockContext, "str");
    verify(mockSharedPreferences, times(1)).getString("str", "");
  }

  @Test
  public void test_getValueFromCacheBool() {
    CacheUtil.getInstance().getValueFromCacheBool(mockContext, "bool");
    verify(mockSharedPreferences, times(1)).getBoolean("bool", false);
  }
}
