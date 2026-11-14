package live.ptiv.watch;

import android.app.PendingIntent;
import android.content.Intent;
import android.graphics.drawable.Icon;
import androidx.wear.watchface.complications.data.ComplicationData;
import androidx.wear.watchface.complications.data.ComplicationText;
import androidx.wear.watchface.complications.data.ComplicationType;
import androidx.wear.watchface.complications.data.LongTextComplicationData;
import androidx.wear.watchface.complications.data.MonochromaticImage;
import androidx.wear.watchface.complications.data.MonochromaticImageComplicationData;
import androidx.wear.watchface.complications.data.PlainComplicationText;
import androidx.wear.watchface.complications.data.ShortTextComplicationData;
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceService;
import androidx.wear.watchface.complications.datasource.ComplicationRequest;

/** A static, low-battery, one-tap app shortcut. The watch face never needs the phone. */
public final class PTIVShortcutComplication extends ComplicationDataSourceService {
    @Override
    public void onComplicationRequest(
            ComplicationRequest request, ComplicationRequestListener listener) {
        try {
            listener.onComplicationData(data(request.getComplicationType()));
        } catch (android.os.RemoteException error) {
            android.util.Log.w("PTIVComplication", "Complication receiver unavailable", error);
        }
    }

    @Override
    public ComplicationData getPreviewData(ComplicationType type) {
        return data(type);
    }

    private ComplicationData data(ComplicationType type) {
        Intent launch = new Intent(this, WatchActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent action = PendingIntent.getActivity(
                this, 2026, launch, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        MonochromaticImage symbol = new MonochromaticImage.Builder(
                Icon.createWithResource(this, R.drawable.ptiv_complication)).build();
        ComplicationText description =
                new PlainComplicationText.Builder("Open P.T. Architech Walkman").build();

        if (type == ComplicationType.SHORT_TEXT) {
            return new ShortTextComplicationData.Builder(
                    new PlainComplicationText.Builder("PTIV").build(), description)
                    .setMonochromaticImage(symbol)
                    .setTapAction(action)
                    .build();
        }
        if (type == ComplicationType.LONG_TEXT) {
            return new LongTextComplicationData.Builder(
                    new PlainComplicationText.Builder("P.T. Architech Walkman").build(), description)
                    .setMonochromaticImage(symbol)
                    .setTapAction(action)
                    .build();
        }
        if (type == ComplicationType.MONOCHROMATIC_IMAGE) {
            return new MonochromaticImageComplicationData.Builder(symbol, description)
                    .setTapAction(action)
                    .build();
        }
        // If the current watch-face slot doesn't support the provider's data types,
        // return no data rather than fabricate an unsupported complication.
        return null;
    }
}
