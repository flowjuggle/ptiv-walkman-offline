package live.ptiv.shortcut;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.graphics.Color;
import android.widget.TextView;

/** One-tap launcher for the already-installed PTIV Walkman, not a second player. */
public final class ShortcutActivity extends Activity {
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        try {
            Intent intent = new Intent(Intent.ACTION_MAIN);
            intent.setClassName("live.ptiv.watch", "live.ptiv.watch.WatchActivity");
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
            startActivity(intent);
            finish();
        } catch (Exception ex) {
            TextView message = new TextView(this);
            message.setText("P.T. Architech Walkman is not installed.\nThis shortcut does not replace your music player.");
            message.setTextColor(Color.WHITE);
            message.setTextSize(17f);
            message.setPadding(20, 42, 20, 20);
            message.setBackgroundColor(Color.rgb(6, 23, 30));
            setContentView(message);
        }
    }
}
