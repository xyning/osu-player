package pw.lictex.osuplayer;

import android.app.*;
import android.content.*;
import android.os.*;
import android.view.*;
import android.view.inputmethod.*;

import androidx.core.app.*;

import com.annimon.stream.*;

/**
 * Created by kpx on 2019/9/12.
 */
public class Utils {
    public static int dp2px(Context context, float dpValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return (int) (dpValue * scale + 0.5f);
    }

    public static float px2dp(Context context, int pxValue) {
        final float scale = context.getResources().getDisplayMetrics().density;
        return pxValue / scale + 0.5f;
    }

    public static void clearFocus(Activity a) {
        View current = a.getCurrentFocus();
        if (current != null) current.clearFocus();
    }

    public static void showSoftInput(View v) {
        InputMethodManager inputMethodManager = ActivityCompat.getSystemService(v.getContext(), InputMethodManager.class);
        inputMethodManager.showSoftInput(v, 0);
    }

    public static void hideSoftInput(View v) {
        InputMethodManager inputMethodManager = ActivityCompat.getSystemService(v.getContext(), InputMethodManager.class);
        inputMethodManager.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    public static int getPreferenceStringAsInt(SharedPreferences preferences, String k, int def) {
        int i = def;
        try {
            i = Integer.parseInt(java.util.Objects.requireNonNull(preferences.getString(k, null)));
        } catch (Throwable e) {
            preferences.edit().putString(k, String.valueOf(def)).apply();
        }
        return i;
    }

    public static void runTask(Runnable r) {
        new Task(r).execute();
    }

    private static class Task extends AsyncTask<Void, Void, Void> {
        private Runnable r;

        public Task(Runnable r) {
            this.r = r;
        }

        @Override protected Void doInBackground(Void... voids) {
            try {
                r.run();
            } catch (Throwable e) {
                e.printStackTrace();
            }
            return null;
        }
    }

    public static class LimitedStack<T> {
        private Object[] buffer;
        private int head;
        private int size;

        public LimitedStack(int size) {
            buffer = new Object[size];
            clear();
        }

        public void clear() {
            head = -1;
            size = 0;
        }

        public LimitedStack<T> skip(int i) {
            for (int r = 0; r < i; r++) {
                if (size < 1) break;
                head = head > 0 ? head - 1 : buffer.length - 1; size--;
            }
            return this;
        }

        public Optional<T> get(int pos) {
            if (pos >= size || pos < 0) return Optional.empty();
            return Optional.ofNullable((T) buffer[head - pos + (head - pos < 0 ? buffer.length : 0)]);
        }

        public Optional<T> pop() {
            if (size == 0) return Optional.empty();
            T t = get(0).orElse(null);
            head = head > 0 ? head - 1 : buffer.length - 1; size--;
            return Optional.ofNullable(t);
        }

        public T push(T obj) {
            head = head + 1 < buffer.length ? head + 1 : 0;
            buffer[head] = obj;
            size = size < buffer.length ? size + 1 : size;
            return obj;
        }
    }
}
