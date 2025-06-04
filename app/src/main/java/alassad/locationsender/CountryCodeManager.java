package alassad.locationsender;

import android.content.Context;
import org.json.JSONArray;
import org.json.JSONObject;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public class CountryCodeManager {
    private final List<CountryItem> countryItems = new ArrayList<>();

    public static class CountryItem {
        public final String name;
        public final String code;
        public CountryItem(String name, String code) {
            this.name = name;
            this.code = code;
        }
        @Override
        public String toString() {
            return name + " (" + code + ")";
        }
    }

    public List<CountryItem> getCountryItems() {
        return Collections.unmodifiableList(countryItems);
    }

    public void loadCountryCodes(Context context) {
        try (InputStream is = context.getResources().openRawResource(R.raw.country_codes)) {
            countryItems.clear();
            byte[] buffer = new byte[is.available()];
            is.read(buffer);
            JSONArray arr = new JSONArray(new String(buffer, "UTF-8"));
            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.getJSONObject(i);
                countryItems.add(new CountryItem(obj.getString("name"), obj.getString("code")));
            }
            countryItems.sort((a, b) -> a.name.compareToIgnoreCase(b.name));
        } catch (Exception e) {
            // Handle error as needed
        }
    }
}
