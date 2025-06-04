package alassad.locationsender;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;

public class WhatsAppSender {
    public void sendLocation(Context context, String countryCode, String phoneNumber, double latitude, double longitude) {
        try {
            String code = countryCode.replace("+", "");
            String locationUri = "http://maps.google.com/maps?q=loc:" + latitude + "," + longitude;
            Uri uri = Uri.parse("https://api.whatsapp.com/send?phone=" + code + phoneNumber + "&text=" + Uri.encode(locationUri));
            Intent sendIntent = new Intent(Intent.ACTION_VIEW, uri);
            sendIntent.setPackage("com.whatsapp");
            context.startActivity(sendIntent);
        } catch (Exception ex) {
            // Handle error as needed
        }
    }
}
