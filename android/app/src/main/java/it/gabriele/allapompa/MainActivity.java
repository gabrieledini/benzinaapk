package it.gabriele.allapompa;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.net.Uri;
import android.os.Bundle;
import android.webkit.WebResourceRequest;
import android.webkit.WebResourceResponse;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import androidx.webkit.WebViewAssetLoader;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;

/**
 * La pagina è identica a quella web, ma qui le richieste al MIMIT non passano
 * dalla WebView: vengono eseguite in Java e reimmesse nella pagina con gli
 * header CORS aggiunti a mano.
 *
 * Serve perché il server del MIMIT, dal 16 ottobre 2026, risponde solo a
 * indirizzi IP italiani: dallo smartphone è raggiungibile, da un server
 * all'estero no. Il CORS invece è una regola del browser, e un client HTTP
 * nativo semplicemente non la applica.
 *
 * La pagina viene servita da https://appassets.androidplatform.net/ invece che
 * da file:// — altrimenti l'origine sarebbe "null" e le fetch verrebbero
 * respinte comunque.
 */
public class MainActivity extends Activity {

    private static final String PAGINA =
            "https://appassets.androidplatform.net/assets/index.html";

    /** Host le cui richieste vengono eseguite in nativo. */
    private static final String[] NATIVI = {
            "www.mimit.gov.it",
            "carburanti.mise.gov.it"
    };

    private static final String UA =
            "Mozilla/5.0 (Linux; Android 13) AppleWebKit/537.36 (KHTML, like Gecko) "
          + "Chrome/120.0.0.0 Mobile Safari/537.36";

    private WebView web;

    @SuppressLint("SetJavaScriptEnabled")
    @Override
    protected void onCreate(Bundle stato) {
        super.onCreate(stato);

        final WebViewAssetLoader caricatore = new WebViewAssetLoader.Builder()
                .addPathHandler("/assets/", new WebViewAssetLoader.AssetsPathHandler(this))
                .build();

        web = new WebView(this);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);          // la pagina conserva lo storico qui
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);
        s.setSupportZoom(false);
        s.setCacheMode(WebSettings.LOAD_DEFAULT);

        web.setWebViewClient(new WebViewClient() {
            @Override
            public WebResourceResponse shouldInterceptRequest(WebView view, WebResourceRequest req) {
                Uri u = req.getUrl();
                if (daFareInNativo(u.getHost())) {
                    WebResourceResponse r = scarica(u.toString(), req.getMethod());
                    if (r != null) return r;     // in caso di errore si lascia provare alla WebView
                }
                return caricatore.shouldInterceptRequest(u);
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest req) {
                // i link esterni (mappe) escono dall'app
                String host = req.getUrl().getHost();
                if (host != null && !host.equals("appassets.androidplatform.net")) {
                    try {
                        startActivity(new android.content.Intent(
                                android.content.Intent.ACTION_VIEW, req.getUrl()));
                        return true;
                    } catch (Exception ignored) { }
                }
                return false;
            }
        });

        setContentView(web);
        web.loadUrl(PAGINA);
    }

    private static boolean daFareInNativo(String host) {
        if (host == null) return false;
        for (String h : NATIVI) if (host.equalsIgnoreCase(h)) return true;
        return false;
    }

    /**
     * Esegue la richiesta con HttpURLConnection e la restituisce alla pagina
     * con Access-Control-Allow-Origin, così la WebView la accetta.
     * Gira già su un thread di lavoro: shouldInterceptRequest non è sul thread UI.
     */
    private WebResourceResponse scarica(String indirizzo, String metodo) {
        HttpURLConnection c = null;
        try {
            c = (HttpURLConnection) new URL(indirizzo).openConnection();
            c.setRequestMethod(metodo == null ? "GET" : metodo);
            c.setInstanceFollowRedirects(true);
            c.setConnectTimeout(20000);
            c.setReadTimeout(90000);
            c.setRequestProperty("User-Agent", UA);
            c.setRequestProperty("Accept", "text/csv,text/plain,application/json,*/*");
            c.setRequestProperty("Accept-Language", "it-IT,it;q=0.9");

            int codice = c.getResponseCode();
            InputStream corpo = (codice >= 400) ? c.getErrorStream() : c.getInputStream();
            if (corpo == null) corpo = new ByteArrayInputStream(new byte[0]);

            String tipo = c.getContentType();
            String mime = "text/plain", charset = "utf-8";
            if (tipo != null) {
                String[] parti = tipo.split(";");
                mime = parti[0].trim();
                for (String p : parti) {
                    String t = p.trim().toLowerCase();
                    if (t.startsWith("charset=")) charset = t.substring(8).replace("\"", "");
                }
            }

            Map<String, String> header = new HashMap<>();
            header.put("Access-Control-Allow-Origin", "*");
            header.put("Access-Control-Allow-Methods", "GET, HEAD, OPTIONS");
            header.put("Access-Control-Allow-Headers", "*");
            header.put("Cache-Control", "no-cache");

            String ragione = c.getResponseMessage();
            if (ragione == null || ragione.isEmpty()) ragione = "OK";

            return new WebResourceResponse(mime, charset, codice, ragione, header, corpo);
        } catch (Exception e) {
            if (c != null) c.disconnect();
            // errore di rete: lo segnaliamo alla pagina come 599, che la fetch gestisce
            Map<String, String> header = new HashMap<>();
            header.put("Access-Control-Allow-Origin", "*");
            byte[] msg = ("errore di rete: " + e.getMessage()).getBytes();
            return new WebResourceResponse("text/plain", "utf-8", 599, "Errore di rete",
                    header, new ByteArrayInputStream(msg));
        }
    }

    @Override
    public void onBackPressed() {
        if (web != null && web.canGoBack()) web.goBack();
        else super.onBackPressed();
    }
}
