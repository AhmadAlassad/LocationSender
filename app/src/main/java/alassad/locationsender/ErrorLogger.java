package alassad.locationsender;

import android.content.Context;
import android.util.Log;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;

public class ErrorLogger {
    private static final String TAG = "ErrorLogger";
    public static void logErrorToFile(Context context, Exception e) {
        File logFile = new File(context.getFilesDir(), "ERRORS");
        try (FileWriter writer = new FileWriter(logFile, true)) {
            writer.write("Localized Message: \n" + e.getMessage() + "\n");
            writer.write("Message: \n" + e.getMessage() + "\n");
            writer.write("Cause: \n" + e.getCause() + "\n");
            writer.write("StackTrace: \n" + Log.getStackTraceString(e) + "\n\n");
        } catch (IOException ioException) {
            Log.e(TAG, "Failed to write to log file", ioException);
        }
    }
}
