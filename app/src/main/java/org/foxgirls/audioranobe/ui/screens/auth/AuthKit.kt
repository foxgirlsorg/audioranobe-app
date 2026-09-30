package org.foxgirls.audioranobe.ui.screens.auth

import android.annotation.SuppressLint
import android.webkit.JavascriptInterface
import android.webkit.WebChromeClient
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.ClipEntry
import androidx.compose.ui.platform.LocalClipboard
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import org.foxgirls.audioranobe.core.Api
import org.foxgirls.audioranobe.data.Me
import org.foxgirls.audioranobe.data.ProviderInfo
import org.foxgirls.audioranobe.data.Stores
import org.foxgirls.audioranobe.ui.LocalBottomInset
import org.foxgirls.audioranobe.ui.components.ArButton
import org.foxgirls.audioranobe.ui.components.ArModal
import org.foxgirls.audioranobe.ui.components.ArTextField
import org.foxgirls.audioranobe.ui.components.ButtonKind
import org.foxgirls.audioranobe.ui.components.Eyebrow
import org.foxgirls.audioranobe.ui.components.HairlineDivider
import org.foxgirls.audioranobe.ui.components.IconBtn
import org.foxgirls.audioranobe.ui.components.Spinner
import org.foxgirls.audioranobe.ui.icons.Lucide
import org.foxgirls.audioranobe.ui.nav.LocalNav
import org.foxgirls.audioranobe.ui.nav.Routes
import org.foxgirls.audioranobe.ui.theme.Ar
import org.foxgirls.audioranobe.ui.toast.toast
import org.foxgirls.audioranobe.ui.toast.toastError
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.nio.ByteBuffer

/**
 * The frame every auth page uses. On the site this is a glass card; on the phone the form is
 * a plain, vertically centred page (no card, no modal feel). Content scrolls when it does not fit
 * or the keyboard is up. [showBack] is off for the two gate screens, which have nowhere to go back to.
 */
@Composable
fun AuthCard(title: String, accent: String, eyebrow: String? = null, formError: String? = null, showBack: Boolean = true, content: @Composable ColumnScope.() -> Unit) {
    val nav = LocalNav.current
    val bottom = LocalBottomInset.current
    BoxWithConstraints(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
        val viewport = maxHeight
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).heightIn(min = viewport).padding(horizontal = 22.dp).padding(top = if (showBack) 60.dp else 16.dp, bottom = bottom + 24.dp),
            verticalArrangement = Arrangement.Center,
        ) {
            if (eyebrow != null) { Eyebrow(eyebrow); Spacer(Modifier.height(6.dp)) }
            Row { Text(title, color = Ar.white, fontSize = 28.sp, fontWeight = FontWeight.Light); Text(" $accent", color = Ar.accent, fontSize = 28.sp, fontWeight = FontWeight.Normal) }
            Spacer(Modifier.height(20.dp))
            if (!formError.isNullOrBlank()) {
                Text(formError, color = Ar.danger, fontSize = 13.sp, lineHeight = 18.sp, modifier = Modifier.fillMaxWidth().background(Ar.danger.copy(alpha = 0.1f), RoundedCornerShape(8.dp)).padding(10.dp))
                Spacer(Modifier.height(12.dp))
            }
            content()
        }
        // Pinned to the top-left, above the scrolling form.
        if (showBack) IconBtn(Lucide.ArrowLeft, "Назад", { nav.back() }, modifier = Modifier.padding(start = 10.dp, top = 6.dp), tint = Ar.text)
    }
}

@Composable
fun AltLink(text: String, onClick: () -> Unit, prefix: String? = null, modifier: Modifier = Modifier) {
    Row(modifier.padding(top = 12.dp), horizontalArrangement = Arrangement.Center) {
        if (prefix != null) Text("$prefix ", color = Ar.textMuted, fontSize = 13.sp)
        Text(text, color = Ar.accent, fontSize = 13.sp, fontWeight = FontWeight.SemiBold, modifier = Modifier.clickable(onClick = onClick))
    }
}

/**
 * components/Captcha: the admin-configured widget script (Turnstile / reCAPTCHA / hCaptcha) in a WebView.
 * The page is served with the site as base URL so the widget's domain checks pass; the solved token
 * comes back over a JS bridge. Renders nothing when captcha is off.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CaptchaWidget(nonce: Int, onToken: (String) -> Unit) {
    val config by Stores.config.config.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { Stores.config.ensure() }
    val c = config?.captcha ?: return
    if (!c.enabled || c.site_key.isBlank() || c.script_url.isBlank() || c.widget_var.isBlank()) return
    val html = remember(c, nonce) {
        """<!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1">
        <style>html,body{margin:0;background:#161616;color:#a6acb2;font:13px sans-serif}#w{padding:6px 0}</style>
        <script src="${c.script_url}" async defer onload="init()"></script>
        <script>
        function init(){var api=window["${c.widget_var}"];if(!api||typeof api.render!=='function'){setTimeout(init,300);return;}
          api.render(document.getElementById('w'),{sitekey:"${c.site_key}",theme:'dark',
            callback:function(t){Android.onToken(t)},'expired-callback':function(){Android.onToken('')},'error-callback':function(){Android.onToken('')}});
          document.getElementById('l').style.display='none';}
        </script></head><body><span id="l">Загружаем проверку…</span><div id="w"></div></body></html>"""
    }
    AndroidView(
        modifier = Modifier.fillMaxWidth().height(90.dp).padding(bottom = 8.dp),
        factory = { ctx ->
            WebView(ctx).apply {
                settings.javaScriptEnabled = true
                settings.domStorageEnabled = true
                setBackgroundColor(0xFF161616.toInt())
                webChromeClient = WebChromeClient()
                webViewClient = WebViewClient()
                addJavascriptInterface(object { @JavascriptInterface fun onToken(t: String) { post { onToken(t) } } }, "Android")
                loadDataWithBaseURL(Api.siteUrl + "/", html, "text/html", "utf-8", null)
            }
        },
        update = { wv -> if (wv.tag != nonce) { wv.tag = nonce; wv.loadDataWithBaseURL(Api.siteUrl + "/", html, "text/html", "utf-8", null) } },
    )
}

@Serializable
private data class OAuthUrl(val url: String)

/** components/ProviderAuth: one button per configured OAuth provider. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProviderButtons(mode: String, providers: List<ProviderInfo>? = null, hide: List<String> = emptyList()) {
    val config by Stores.config.config.collectAsStateWithLifecycle()
    LaunchedEffect(providers == null) { if (providers == null) Stores.config.ensure() }
    val list = (providers ?: config?.auth_providers ?: emptyList()).filter { it.id !in hide }
    if (list.isEmpty()) return
    val nav = LocalNav.current
    Column(Modifier.fillMaxWidth().padding(top = 16.dp)) {
        if (mode == "login") Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(bottom = 12.dp)) {
            HairlineDivider(Modifier.weight(1f)); Text("  или  ", color = Ar.textMuted, fontSize = 12.sp); HairlineDivider(Modifier.weight(1f))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
            for (p in list) {
                Row(
                    Modifier.clip(RoundedCornerShape(10.dp)).background(Ar.fill04).border(1.dp, Ar.border, RoundedCornerShape(10.dp)).clickable { nav.go(Routes.oauth(p.id, mode)) }
                        .height(44.dp).padding(horizontal = if (mode == "link") 14.dp else 18.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    val svg = remember(p.icon_svg) { ByteBuffer.wrap(p.icon_svg.toByteArray()) }
                    if (p.icon_svg.isNotBlank()) AsyncImage(model = svg, contentDescription = p.name, modifier = Modifier.size(20.dp))
                    else Text(p.name.take(1), color = Ar.text, fontWeight = FontWeight.Bold)
                    if (mode == "link") { Spacer(Modifier.width(8.dp)); Text("Привязать ${p.name}", color = Ar.text, fontSize = 13.sp) }
                }
            }
        }
    }
}

@Serializable
private data class TotpSetupRes(val secret: String = "", val otpauth_url: String = "")

@Serializable
private data class TotpConfirmRes(val user: Me, val backup_codes: List<String> = emptyList())

/** components/TotpSetupModal: generate secret → scan QR → confirm → backup codes. */
@Composable
fun TotpSetupModal(open: Boolean, onClose: () -> Unit, onEnabled: (Me) -> Unit) {
    if (!open) return
    var step by remember { mutableStateOf("loading") }
    var secret by remember { mutableStateOf("") }
    var otpauth by remember { mutableStateOf("") }
    var code by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var codes by remember { mutableStateOf<List<String>>(emptyList()) }
    val clipboard = LocalClipboard.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) {
        try { val r = Api.post<TotpSetupRes>("/me/totp/setup"); secret = r.secret; otpauth = r.otpauth_url; step = "scan" } catch (e: Exception) { toastError(e); onClose() }
    }
    val qr = remember(otpauth) {
        if (otpauth.isBlank()) null else runCatching {
            val m = QRCodeWriter().encode(otpauth, BarcodeFormat.QR_CODE, 440, 440)
            val bmp = android.graphics.Bitmap.createBitmap(m.width, m.height, android.graphics.Bitmap.Config.ARGB_8888)
            for (x in 0 until m.width) for (y in 0 until m.height) bmp.setPixel(x, y, if (m.get(x, y)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt())
            bmp.asImageBitmap()
        }.getOrNull()
    }
    ArModal(true, onClose, "Двухфакторная аутентификация") {
        when (step) {
            "loading" -> Text("Готовим секрет…", color = Ar.textMuted, fontSize = 13.sp)
            "scan" -> {
                Text("Отсканируйте QR-код в приложении-аутентификаторе (Google Authenticator, Aegis и т.п.) или введите код вручную:", color = Ar.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(12.dp))
                if (qr != null) Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) { androidx.compose.foundation.Image(qr, "QR-код для настройки 2FA", Modifier.size(200.dp).clip(RoundedCornerShape(8.dp)).background(Color.White)) }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth().background(Ar.fill04, RoundedCornerShape(8.dp)).padding(start = 12.dp, end = 4.dp, top = 4.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(secret, color = Ar.text, fontSize = 13.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.weight(1f))
                    IconBtn(Lucide.Copy, "Скопировать секрет", { scope.launch { clipboard.setClipEntry(ClipEntry(android.content.ClipData.newPlainText("", secret))) }; toast("Скопировано") }, size = 34.dp, iconSize = 14.dp)
                }
                Spacer(Modifier.height(12.dp))
                ArTextField(code, { code = it.take(6) }, label = "Код из приложения", placeholder = "000000", keyboardType = KeyboardType.Number)
                Spacer(Modifier.height(14.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ArButton("Отмена", onClose, kind = ButtonKind.Ghost)
                    Spacer(Modifier.width(8.dp))
                    ArButton(if (busy) "Проверяем…" else "Подтвердить", {
                        if (busy || code.isBlank()) return@ArButton
                        busy = true
                        scope.launch {
                            try { val r = Api.post<TotpConfirmRes>("/me/totp/confirm", buildJsonObject { put("code", code.trim()) }); codes = r.backup_codes; step = "codes"; onEnabled(r.user) } catch (e: Exception) { toastError(e) } finally { busy = false }
                        }
                    }, kind = ButtonKind.Primary, busy = busy, enabled = code.isNotBlank())
                }
            }
            else -> {
                Text("Двухфакторная аутентификация включена. Сохраните эти запасные коды в надёжном месте — каждый работает один раз и заменяет код из приложения, если вы потеряете доступ к нему. Больше они не будут показаны.", color = Ar.textSecondary, fontSize = 13.sp, lineHeight = 18.sp)
                Spacer(Modifier.height(12.dp))
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    for (c in codes) Text(c, color = Ar.text, fontSize = 13.sp, fontFamily = FontFamily.Monospace, modifier = Modifier.background(Ar.fill04, RoundedCornerShape(6.dp)).padding(horizontal = 10.dp, vertical = 6.dp))
                }
                Spacer(Modifier.height(12.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    ArButton("Скопировать все", { scope.launch { clipboard.setClipEntry(ClipEntry(android.content.ClipData.newPlainText("", codes.joinToString("\n")))) }; toast("Скопировано") }, kind = ButtonKind.Ghost, icon = Lucide.Copy)
                    Spacer(Modifier.width(8.dp))
                    ArButton("Готово", onClose, kind = ButtonKind.Primary)
                }
            }
        }
    }
}

@Composable
fun LoadingCard(text: String) {
    Column(Modifier.fillMaxSize().statusBarsPadding().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Spinner(size = 30.dp)
        Spacer(Modifier.height(12.dp))
        Text(text, color = Ar.textMuted, fontSize = 13.sp)
    }
}
