package alassad.locationsender;

import android.graphics.Bitmap;

public interface ILocationPreviewManager {
    interface Callback {
        void onAddressFetched(String address, String statusText);
        void onAddressError(String errorMsg);
        void onMapLoaded(Bitmap bitmap);
        void onMapError(String errorMsg);
    }
    void fetchAddress(double latitude, double longitude, Callback callback);
    void loadMapPreview(double latitude, double longitude, Callback callback);
}
