package alassad.locationsender;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Geocoder;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;
import android.widget.Toast;

import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class LocationPreviewManager implements ILocationPreviewManager {
    private final Context context;
    private final Geocoder geocoder;
    private final String geoapifyApiKey;

    public LocationPreviewManager(Context context, String geoapifyApiKey) {
        this.context = context;
        this.geocoder = new Geocoder(context, Locale.getDefault());
        this.geoapifyApiKey = geoapifyApiKey;
    }

    @Override
    public void fetchAddress(double latitude, double longitude, ILocationPreviewManager.Callback callback) {
        if (!Geocoder.isPresent()) {
            callback.onAddressError(context.getString(R.string.geocoder_not_available));
            return;
        }
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            callback.onAddressError(context.getString(R.string.error_prefix) + context.getString(R.string.invalid_coordinates_message));
            return;
        }
        new Thread(() -> {
            try {
                List<Address> addresses = geocoder.getFromLocation(latitude, longitude, 1);
                if (addresses != null && !addresses.isEmpty()) {
                    Address address = addresses.get(0);
                    StringBuilder addressText = new StringBuilder();
                    for (int i = 0; i <= address.getMaxAddressLineIndex(); i++) {
                        addressText.append(address.getAddressLine(i));
                        if (i < address.getMaxAddressLineIndex()) {
                            addressText.append(", ");
                        }
                    }
                    String status = context.getString(R.string.status_location_updated, addressText.toString());
                    new Handler(Looper.getMainLooper()).post(() -> callback.onAddressFetched(addressText.toString(), status));
                } else {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onAddressError(context.getString(R.string.address_not_found)));
                }
            } catch (Exception e) {
                new Handler(Looper.getMainLooper()).post(() -> callback.onAddressError(context.getString(R.string.error_prefix) + e.getMessage()));
                Log.e("LocationPreviewMgr", "Geocoder error", e);
                ErrorLogger.logErrorToFile(context, e);
            }
        }).start();
    }

    @Override
    public void loadMapPreview(double latitude, double longitude, ILocationPreviewManager.Callback callback) {
        if (latitude < -90 || latitude > 90 || longitude < -180 || longitude > 180) {
            callback.onMapError(context.getString(R.string.error_prefix) + context.getString(R.string.invalid_coordinates_message));
            return;
        }
        String mapUrl = "https://maps.geoapify.com/v1/staticmap?style=osm-carto&width=600&height=300&center=lonlat:"
                + longitude + "," + latitude + "&zoom=16&marker=lonlat:" + longitude + "," + latitude + ";type:awesome;color:red;size:medium&apiKey=" + geoapifyApiKey;
        new Thread(() -> {
            try {
                URL url = new URL(mapUrl);
                HttpURLConnection connection = (HttpURLConnection) url.openConnection();
                connection.setDoInput(true);
                connection.connect();
                InputStream input = connection.getInputStream();
                Bitmap bitmap = BitmapFactory.decodeStream(input);
                input.close();
                connection.disconnect();
                if (bitmap != null) {
                    new Handler(Looper.getMainLooper()).post(() -> callback.onMapLoaded(bitmap));
                } else {
                    throw new Exception("Bitmap is null after decoding stream.");
                }
            } catch (Exception e) {
                Log.e("LocationPreviewMgr", "Error loading map image from URL: " + mapUrl, e);
                ErrorLogger.logErrorToFile(context, e);
                new Handler(Looper.getMainLooper()).post(() -> callback.onMapError(context.getString(R.string.map_preview_load_failed)));
            }
        }).start();
    }
}
