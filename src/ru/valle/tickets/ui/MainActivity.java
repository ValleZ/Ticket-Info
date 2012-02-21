package ru.valle.tickets.ui;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.IntentFilter.MalformedMimeTypeException;
import android.nfc.NfcAdapter;
import android.nfc.Tag;
import android.nfc.tech.MifareUltralight;
import android.os.AsyncTask;
import android.os.Bundle;
import android.text.Html;
import android.util.Log;
import android.widget.TextView;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;
import ru.valle.tickets.R;

/**
 * Dialog to display version information and traffic counters
 */
public final class MainActivity extends Activity {

    static final String TAG = "tickets";
    private TextView text;
    private NfcAdapter adapter;
    private PendingIntent pendingIntent;
    private IntentFilter[] filters;
    private String[][] techLists;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);
        text = (TextView) findViewById(R.id.body);
        df = new SimpleDateFormat("dd MMMM yyyy");
        onNewIntent(getIntent());
        adapter = NfcAdapter.getDefaultAdapter(this);
        pendingIntent = PendingIntent.getActivity(this, 0,
                new Intent(this, getClass()).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP), 0);
        IntentFilter filter = new IntentFilter(NfcAdapter.ACTION_TECH_DISCOVERED);
        try {
            filter.addDataType("*/*");
        } catch (MalformedMimeTypeException e) {
            Log.e(TAG, "fail", e);
        }
        filters = new IntentFilter[]{filter};
        techLists = new String[][]{new String[]{MifareUltralight.class.getName()}};
        
        
        
       

    }

    @Override
    public void onResume() {
        super.onResume();
        df = new SimpleDateFormat("dd MMMM yyyy");
        adapter.enableForegroundDispatch(this, pendingIntent, filters, techLists);
    }

    @Override
    public void onPause() {
        super.onPause();
        adapter.disableForegroundDispatch(this);
    }

    @Override
    public void onNewIntent(Intent intent) {
        if (intent != null && intent.getAction().equals(NfcAdapter.ACTION_TECH_DISCOVERED)) {
            try {
                Bundle extras = intent.getExtras();
                Tag tag = (Tag) extras.get(NfcAdapter.EXTRA_TAG);
                final MifareUltralight ultralight = MifareUltralight.get(tag);
                text.setText(getString(R.string.ticket_is_reading));
                new AsyncTask<MifareUltralight, Void, String>() {

                    @Override
                    protected String doInBackground(MifareUltralight... paramss) {
                        try {
                            ultralight.connect();
                            byte[] pages3bytes = ultralight.readPages(3);
                            byte[] pages8bytes = ultralight.readPages(8);
                            ultralight.close();
                            return decodeUltralight(toIntPages(pages3bytes), toIntPages(pages8bytes));
                        } catch (Throwable th) {
                            return getString(R.string.ticket_read_error);
                        }
                    }

                    @Override
                    protected void onPostExecute(String result) {
                        text.setText(result);
                    }
                }.execute(ultralight);

            } catch (Throwable th) {
                text.setText(getString(R.string.ticket_read_error));
                Log.e(TAG, "read err", th);
            }
        } else {
            text.setText(getString(R.string.ticket_disclaimer));
        }
    }

    public String decodeUltralight(int[] pages3, int[] pages8) {
        int p3 = pages3[0];
        int p4 = pages3[1];
        int p5 = pages3[2];
        int p6 = pages3[3];
        int p8 = pages8[0];
        int p9 = pages8[1];
        int p10 = pages8[2];
        StringBuilder sb = new StringBuilder();
        sb.append(descAppId(this, p4 >>> 22)).append('\n');
        sb.append(descCardType(this, (p4 >>> 12) & 0x3ff)).append('\n');
        int mask12 = 0;
        for (int i = 0; i < 12; i++) {
            mask12 <<= 1;
            mask12 |= 1;
        }
        long cardId = ((p4 & mask12) << 20) | (p5 >>> 12);
        sb.append(getString(R.string.ticket_num)).append(' ').append(cardId).append('\n');
        int cardLayout = ((p5 >> 8) & 0xf);
        if (cardLayout == 8) {
            sb.append(getString(R.string.passes_left)).append(": ").append(p9 >>> 16).append('\n');
            sb.append(getString(R.string.issued)).append(": ").append(getReadableDate((p8 >>> 16) & 0xffff)).append('\n');
            sb.append(getString(R.string.best_in_days)).append(": ").append((p8 >>> 8) & 0xff).append('\n');
            sb.append(getString(R.string.station_last_enter)).append(": ").append(getGateDesc(p9 & 0xffff)).append('\n');
            sb.append(getString(R.string.ticket_hash)).append(": ").append(Integer.toHexString(p10)).append('\n');
            sb.append(getString(R.string.ticket_blank_best_before)).append(": ").append(getReadableDate(((p5 & 0xff) << 8) | (p6 >>> 24))).append('\n');
        } else {
            sb.append(getString(R.string.unknown_layout)).append(": ").append(cardLayout).append('\n');
        }
        sb.append(getString(R.string.otp)).append(": ").append(Integer.toBinaryString(p3)).append('\n');
        return sb.toString();
    }
    private DateFormat df;

    private String getReadableDate(int days) {
        Calendar c = Calendar.getInstance();
        c.clear();
        c.set(1992, 1, 1);
        c.add(Calendar.DATE, days);
        return df.format(c.getTime());
    }

    private static String descCardType(Context c, int ct) {
        switch (ct) {
            case 120:
                return "1 " + getNounCase(1, R.array.trip_cases, c);
            case 121:
                return "2 " + getNounCase(2, R.array.trip_cases, c);
            case 122:
                return "3 " + getNounCase(3, R.array.trip_cases, c);
            case 123:
                return "4 " + getNounCase(4, R.array.trip_cases, c);
            case 126:
                return "5 " + getNounCase(5, R.array.trip_cases, c);
            case 127:
                return "10 " + getNounCase(10, R.array.trip_cases, c);
            case 128:
                return "20 " + getNounCase(20, R.array.trip_cases, c);
            case 129:
                return "60 " + getNounCase(60, R.array.trip_cases, c);
            case 130:
                return c.getString(R.string.baggage_and_pass);
            case 131:
                return c.getString(R.string.baggage_only);
            case 149:
                return c.getString(R.string.universal_ultralight_70);
            case 150:
                return c.getString(R.string.vesb);
            default:
                return c.getString(R.string.unknown_ticket_category) + ": " + ct;

        }
    }

    private static String descAppId(Context c, int id) {
        switch (id) {
            case 262:
                return c.getString(R.string.ticket_main_type_mosmetro);
            case 264:
                return c.getString(R.string.ticket_main_type_mosground);
            case 266:
                return c.getString(R.string.ticket_main_type_mosuniversal);
            case 270:
                return c.getString(R.string.ticket_main_type_mosmetrolight);
            default:
                return c.getString(R.string.ticket_main_type_unknown) + ": " + id;
        }
    }

    private String getGateDesc(int id) {
        switch (id) {
            case 1230:
            case 2290:
                return "№" +id+" "+trans("Парк культуры (радиальная)");
            case 1228:
            case 2211:
                return "№" +id+" "+trans("Авиамоторная");
            case 2194:
                return "№" +id+" "+trans("Юго-Западная");
            case 2233:
                return "№" +id+" "+trans("Спортивная");
            default:
                return "№" + id;
        }
    }

    private static int[] toIntPages(byte[] pagesBytes) {
        int[] pages = new int[pagesBytes.length / 4];
        for (int pp = 0; pp < pages.length; pp++) {
            int page = 0;
            for (int i = 0; i < 4; i++) {
                page <<= 8;
                page |= pagesBytes[pp * 4 + i] & 0xff;
            }
            pages[pp] = page;
        }
        return pages;
    }

    public static String getNounCase(int n, int arrayID, Context c) {
        n = Math.abs(n);
        String[] cases = c.getResources().getStringArray(arrayID);
        String lang = Locale.getDefault().getLanguage();
        int form = n != 1 ? 2 : 0;
        if ("ru".equals(lang) || "ua".equals(lang) || "be".equals(lang)) {
            form = (n % 10 == 1 && n % 100 != 11 ? 0 : (n % 10) >= 2 && (n % 10) <= 4 && (n % 100 < 10 || n % 100 >= 20) ? 1 : 2);
        } else if ("tr".equals(lang)) {
            form = n > 1 ? 2 : 0;
        }
        return cases[form];
    }

    private String trans(String string) {
        String lang = Locale.getDefault().getLanguage();
        if ("ru".equals(lang) || "ua".equals(lang) || "be".equals(lang)) {
            return string;
        } else {
            StringBuilder sb = new StringBuilder(string.length() * 3 / 2);
            for (int i = 0; i < string.length(); i++) {
                char ch = string.charAt(i);
                int ind = rusAlpha.indexOf(ch);
                if (ind >= 0) {
                    sb.append(transAlpha[ind]);
                } else if ((ind = rusAlphaUpper.indexOf(ch)) >= 0) {
                    String trans = transAlpha[ind];
                    sb.append(Character.toUpperCase(trans.charAt(0)));
                    if (trans.length() > 1) {
                        sb.append(trans.substring(1));
                    }
                } else {
                    sb.append(ch);
                }
            }
            return sb.toString();
        }
    }
    private static final String rusAlpha = "абвгдеёжзиыйклмнопрстуфхцчшщьэюя";
    private static final String rusAlphaUpper = "АБВГДЕЁЖЗИЫЙКЛМНОПРСТУФХЦЧШЩЬЭЮЯ";
    private static final String[] transAlpha = {"a", "b", "v", "g", "d", "e", "yo", "g", "z", "i", "y", "i",
        "k", "l", "m", "n", "o", "p", "r", "s", "t", "u",
        "f", "h", "tz", "ch", "sh", "sh", "'", "e", "yu", "ya"};
}
