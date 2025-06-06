package alassad.locationsender;

import static androidx.constraintlayout.helper.widget.MotionEffect.TAG;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.location.Address;
import android.location.Geocoder;
import android.location.Location;
import android.location.LocationManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.Settings;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.InputMethodManager;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import android.app.AlertDialog;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.google.android.gms.location.FusedLocationProviderClient;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.Priority;
import com.google.android.gms.tasks.CancellationToken;
import com.google.android.gms.tasks.CancellationTokenSource;
import com.google.android.gms.tasks.OnTokenCanceledListener;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;

import alassad.locationsender.databinding.ActivityMainBinding;

/**
 * MainActivity is the entry point for the Location Sender app.
 * It handles UI interactions and delegates business logic to manager classes.
 */
public class MainActivity extends AppCompatActivity {
    private static final String PREFS_NAME = "LocationSenderPrefs";
    private static final String PREF_COUNTRY_CODE = "country_code";
    private static final int LOCATION_PERMISSION_REQUEST_CODE = 1;
    private FusedLocationProviderClient locationProviderClient;
    private String selectedCountryCode;
    private ActivityMainBinding binding;
    private CountryCodeManager countryCodeManager;
    private LocationManagerWrapper locationManagerWrapper;
    private WhatsAppSender whatsAppSender;
    private ProgressBar progressBarMap;
    private Location lastKnownLocation;
    private Geocoder geocoder;
    private ILocationPreviewManager locationPreviewManager;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        binding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(binding.getRoot());

        countryCodeManager = new CountryCodeManager();
        countryCodeManager.loadCountryCodes(this);
        locationManagerWrapper = new LocationManagerWrapper(this); // Remove callbacks
        whatsAppSender = new WhatsAppSender();
        locationProviderClient = LocationServices.getFusedLocationProviderClient(this);
        geocoder = new Geocoder(this, Locale.getDefault());
        locationPreviewManager = new LocationPreviewManager(this, BuildConfig.GEOAPIFY_API_KEY);

        binding.buttonCountryCode.setEnabled(!countryCodeManager.getCountryItems().isEmpty());

        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        selectedCountryCode = prefs.getString(PREF_COUNTRY_CODE, null);
        binding.buttonCountryCode.setText(selectedCountryCode != null ? selectedCountryCode : getString(R.string.default_country_code));

        progressBarMap = binding.progressBarMap;
        binding.imageViewPinOverlay.setVisibility(View.GONE);
        binding.textViewAddress.setText(getString(R.string.address_placeholder));
        binding.textViewLocationPreview.setText(getString(R.string.location_coordinates_placeholder));
        binding.textViewTimestamp.setText(getString(R.string.timestamp_placeholder));

        binding.buttonRefreshLocation.setOnClickListener(v -> {
            binding.textViewStatus.setText(getString(R.string.refreshing_location));
            updateLocationPreview();
        });

        binding.buttonShare.setOnClickListener(v -> {
            String address = binding.textViewAddress.getText().toString();
            String coords = binding.textViewLocationPreview.getText().toString();
            String timestamp = binding.textViewTimestamp.getText().toString();
            String shareText = address + "\n" + coords + "\n" + timestamp;
            Intent shareIntent = new Intent(Intent.ACTION_SEND);
            shareIntent.setType("text/plain");
            shareIntent.putExtra(Intent.EXTRA_TEXT, shareText);
            startActivity(Intent.createChooser(shareIntent, getString(R.string.share_location_title)));
        });

        binding.buttonCountryCode.setOnClickListener(v -> {
            if (countryCodeManager.getCountryItems().isEmpty()) {
                Toast.makeText(this, getString(R.string.country_codes_not_loaded), Toast.LENGTH_SHORT).show();
            } else {
                showCountryCodePicker();
            }
        });

        binding.button.setOnClickListener(v -> handleSendLocation());

        checkPermissions();
    }

    private void handleLocationError(String errorMsg) {
        binding.textViewStatus.setText(errorMsg);
        binding.imageViewPinOverlay.setVisibility(View.GONE);
        binding.progressBarMap.setVisibility(View.GONE);
        // Potentially set placeholder for map image as well
        binding.imageViewMapPreview.setImageResource(android.R.color.darker_gray);
        binding.textViewLocationPreview.setText(getString(R.string.location_coordinates_placeholder));
        binding.textViewAddress.setText(getString(R.string.address_placeholder));
        binding.textViewTimestamp.setText(getString(R.string.timestamp_placeholder));
    }

    /**
     * Updates the location preview UI by requesting the current location and fetching address/map preview.
     * Handles permission and location service checks.
     */
    private void updateLocationPreview() {
        if (!locationManagerWrapper.hasLocationPermission()) {
            binding.textViewStatus.setText(getString(R.string.status_no_permission));
            binding.button.setEnabled(false);
            return;
        }
        if (!locationManagerWrapper.isLocationEnabled()) {
            binding.textViewStatus.setText(getString(R.string.status_location_disabled));
            binding.button.setEnabled(false);
            new AlertDialog.Builder(this)
                    .setMessage(getString(R.string.please_enable_location))
                    .setPositiveButton(getString(R.string.dialog_ok_button), (dialogInterface, i) -> startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                    .setNegativeButton(getString(R.string.dialog_cancel_button), null)
                    .show();
            return;
        }
        binding.textViewStatus.setText(getString(R.string.status_fetching_location));
        binding.button.setEnabled(false);
        binding.progressBarMap.setVisibility(View.VISIBLE);
        binding.imageViewPinOverlay.setVisibility(View.VISIBLE);
        CancellationTokenSource cancellationTokenSource = new CancellationTokenSource();
        locationProviderClient.getCurrentLocation(Priority.PRIORITY_HIGH_ACCURACY, cancellationTokenSource.getToken())
                .addOnSuccessListener(this, location -> {
                    if (location != null) {
                        lastKnownLocation = location;
                        binding.textViewLocationPreview.setText(String.format(Locale.getDefault(), getString(R.string.location_coordinates_format), location.getLatitude(), location.getLongitude()));
                        binding.textViewTimestamp.setText(String.format(Locale.getDefault(), getString(R.string.location_timestamp_format), new SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(new Date(location.getTime()))));
                        locationPreviewManager.fetchAddress(location.getLatitude(), location.getLongitude(), new LocationPreviewManager.Callback() {
                            @Override
                            public void onAddressFetched(String address, String statusText) {
                                binding.textViewAddress.setText(address);
                                binding.textViewStatus.setText(statusText);
                            }
                            @Override
                            public void onAddressError(String errorMsg) {
                                binding.textViewAddress.setText(errorMsg);
                            }
                            @Override
                            public void onMapLoaded(Bitmap bitmap) {
                                binding.imageViewMapPreview.setImageBitmap(bitmap);
                                binding.progressBarMap.setVisibility(View.GONE);
                                binding.imageViewPinOverlay.setVisibility(View.GONE);
                            }
                            @Override
                            public void onMapError(String errorMsg) {
                                Toast.makeText(MainActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                                handleLocationError(errorMsg);
                            }
                        });
                        locationPreviewManager.loadMapPreview(location.getLatitude(), location.getLongitude(), new LocationPreviewManager.Callback() {
                            @Override
                            public void onAddressFetched(String address, String statusText) {}
                            @Override
                            public void onAddressError(String errorMsg) {}
                            @Override
                            public void onMapLoaded(Bitmap bitmap) {
                                binding.imageViewMapPreview.setImageBitmap(bitmap);
                                binding.progressBarMap.setVisibility(View.GONE);
                                binding.imageViewPinOverlay.setVisibility(View.GONE);
                            }
                            @Override
                            public void onMapError(String errorMsg) {
                                Toast.makeText(MainActivity.this, errorMsg, Toast.LENGTH_LONG).show();
                                handleLocationError(errorMsg);
                            }
                        });
                        binding.button.setEnabled(true);
                    } else {
                        binding.textViewStatus.setText(getString(R.string.status_failed_location_null));
                        handleLocationError(getString(R.string.status_failed_location_null));
                    }
                    binding.progressBarMap.setVisibility(View.GONE);
                    binding.imageViewPinOverlay.setVisibility(View.GONE);
                })
                .addOnFailureListener(this, e -> {
                    binding.textViewStatus.setText(getString(R.string.status_failed_location_error, e.getMessage()));
                    handleLocationError(getString(R.string.status_failed_location_error, e.getMessage()));
                    ErrorLogger.logErrorToFile(this, e);
                    binding.progressBarMap.setVisibility(View.GONE);
                    binding.imageViewPinOverlay.setVisibility(View.GONE);
                })
                .addOnCanceledListener(() -> {
                    Log.d(TAG, "Location request canceled.");
                    binding.progressBarMap.setVisibility(View.GONE);
                    binding.imageViewPinOverlay.setVisibility(View.GONE);
                });
        new Handler(Looper.getMainLooper()).postDelayed(cancellationTokenSource::cancel, 10000);
    }

    /**
     * Handles the logic for sending the location via WhatsApp.
     */
    private void handleSendLocation() {
        String phoneNumber = binding.editTextText.getText().toString();
        if (selectedCountryCode == null) {
            Toast.makeText(this, getString(R.string.please_choose_country_code), Toast.LENGTH_LONG).show();
            return;
        }
        if (!locationManagerWrapper.hasLocationPermission()) {
            checkPermissions();
            return;
        }
        if (!locationManagerWrapper.isLocationEnabled()) {
            Toast.makeText(this, getString(R.string.please_enable_location), Toast.LENGTH_SHORT).show();
            return;
        }
        if (!isValidPhoneNumber(phoneNumber)) {
            Toast.makeText(this, getString(R.string.invalid_phone_format), Toast.LENGTH_LONG).show();
            return;
        }
        if (lastKnownLocation == null) {
            Toast.makeText(this, getString(R.string.location_not_available_yet), Toast.LENGTH_LONG).show();
            updateLocationPreview(); // Try to get location again
            return;
        }

        binding.button.setEnabled(false);
        closeKeyboard();

        whatsAppSender.sendLocation(this, selectedCountryCode, phoneNumber, lastKnownLocation.getLatitude(), lastKnownLocation.getLongitude());
        // Re-enable button after a short delay or based on WhatsApp callback if possible
        new Handler(Looper.getMainLooper()).postDelayed(() -> binding.button.setEnabled(true), 2000);
    }

    private boolean isValidPhoneNumber(String phoneNumber) {
        return phoneNumber != null && phoneNumber.startsWith("07") && phoneNumber.length() == 10;
    }

    private void closeKeyboard() {
        View view = getCurrentFocus();
        if (view != null) {
            InputMethodManager imm = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);
            imm.hideSoftInputFromWindow(view.getWindowToken(), 0);
        }
    }

    private void checkPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(this, Manifest.permission.ACCESS_FINE_LOCATION)) {
                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.permission_rationale_title))
                        .setMessage(getString(R.string.permission_rationale_message))
                        .setPositiveButton(getString(R.string.dialog_ok_button), (dialog, which) -> requestLocationPermission())
                        .setNegativeButton(getString(R.string.dialog_cancel_button), (dialog, which) -> {
                            binding.textViewStatus.setText(getString(R.string.status_no_permission));
                            binding.button.setEnabled(false);
                            dialog.dismiss();
                        })
                        .create()
                        .show();
            } else {
                requestLocationPermission();
            }
        } else {
            // Permission already granted
            updateLocationPreview();
        }
    }

    private void requestLocationPermission() {
        ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.ACCESS_FINE_LOCATION}, LOCATION_PERMISSION_REQUEST_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == LOCATION_PERMISSION_REQUEST_CODE) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                // Permission granted
                updateLocationPreview(); // Refresh location dependent UI
            } else {
                // Permission denied
                binding.textViewStatus.setText(getString(R.string.status_no_permission));
                binding.button.setEnabled(false);
                // Optionally, show a dialog explaining why the permission is crucial and offer to go to settings
                new AlertDialog.Builder(this)
                        .setTitle(getString(R.string.permission_denied_title))
                        .setMessage(getString(R.string.permission_denied_message))
                        .setPositiveButton(getString(R.string.action_settings), (dialog, which) -> {
                            // Intent to open app settings
                            Intent intent = new Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
                            Uri uri = Uri.fromParts("package", getPackageName(), null);
                            intent.setData(uri);
                            startActivity(intent);
                        })
                        .setNegativeButton(getString(R.string.dialog_cancel_button), (dialog, which) -> dialog.dismiss())
                        .setCancelable(false) // User must interact with the dialog
                        .show();
            }
        }
    }

    /**
     * MainActivity is the entry point for the Location Sender app.
     * Handles UI interactions and delegates business logic to manager classes.
     */
    public static class ErrorLogger {
        private static final String ERROR_LOG_FILE = "error_log.txt";

        public static void logErrorToFile(Context context, Throwable throwable) {
            try {
                File directory = context.getExternalFilesDir(null);
                if (directory == null) {
                    Log.e(TAG, "External storage directory not found.");
                    return;
                }
                File logFile = new File(directory, ERROR_LOG_FILE);
                FileWriter writer = new FileWriter(logFile, true); // Append mode
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.getDefault());
                writer.append(sdf.format(new Date()));
                writer.append(" - Error: ").append(throwable.toString()).append("\n");
                for (StackTraceElement element : throwable.getStackTrace()) {
                    writer.append("\tat ").append(element.toString()).append("\n");
                }
                Throwable cause = throwable.getCause();
                if (cause != null) {
                    writer.append("Caused by: ").append(cause.toString()).append("\n");
                    for (StackTraceElement element : cause.getStackTrace()) {
                        writer.append("\tat ").append(element.toString()).append("\n");
                    }
                }
                writer.append("\n");
                writer.flush();
                writer.close();
                Log.d(TAG, "Error logged to: " + logFile.getAbsolutePath());
            } catch (IOException e) {
                Log.e(TAG, "Failed to write to error log", e);
            }
        }
    }

    /**
     * Shows the country code picker dialog for the user to select a country code.
     */
    private void showCountryCodePicker() {
        List<CountryCodeManager.CountryItem> countryItems = countryCodeManager.getCountryItems();
        if (countryItems.isEmpty()) {
            Toast.makeText(this, getString(R.string.country_codes_not_loaded_picker), Toast.LENGTH_SHORT).show();
            return;
        }
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(getString(R.string.select_country_code_title));
        View viewInflated = LayoutInflater.from(this).inflate(R.layout.dialog_country_code_picker, (ViewGroup) getWindow().getDecorView(), false);
        final EditText inputSearch = viewInflated.findViewById(R.id.editTextSearchCountry);
        final ListView listViewCountries = viewInflated.findViewById(R.id.listViewCountries);
        List<String> countryStrings = new ArrayList<>();
        for (CountryCodeManager.CountryItem item : countryItems) {
            countryStrings.add(item.toString());
        }
        ArrayAdapter<String> adapter = new ArrayAdapter<>(this, android.R.layout.simple_list_item_1, countryStrings);
        listViewCountries.setAdapter(adapter);
        inputSearch.addTextChangedListener(new android.text.TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                adapter.getFilter().filter(s);
            }
            @Override
            public void afterTextChanged(android.text.Editable s) {}
        });
        builder.setView(viewInflated);
        final AlertDialog dialog = builder.create();
        listViewCountries.setOnItemClickListener((parent, view, position, id) -> {
            String selectedItem = adapter.getItem(position);
            if (selectedItem != null && selectedItem.contains(" (")) {
                int start = selectedItem.indexOf("(");
                int end = selectedItem.indexOf(")", start);
                if (start != -1 && end != -1) {
                    selectedCountryCode = selectedItem.substring(start + 1, end);
                    binding.buttonCountryCode.setText(selectedCountryCode);
                    SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                    prefs.edit().putString(PREF_COUNTRY_CODE, selectedCountryCode).apply();
                }
            }
            dialog.dismiss();
        });
        dialog.show();
    }
}