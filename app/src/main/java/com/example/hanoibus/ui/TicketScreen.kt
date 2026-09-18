package com.example.hanoibus.ui

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColor
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

object TicketAppHelper {
    const val VN_PASS_PACKAGE = "com.vnpass.hn_bus_customer"
    const val WEB_PORTAL_URL = "https://vedientuonline.com.vn"

    fun isAppInstalled(context: Context): Boolean {
        return try {
            context.packageManager.getPackageInfo(VN_PASS_PACKAGE, 0)
            true
        } catch (e: Exception) {
            false
        }
    }

    fun openTicketApp(context: Context): Boolean {
        return try {
            val intent = context.packageManager.getLaunchIntentForPackage(VN_PASS_PACKAGE)
            if (intent != null) {
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                context.startActivity(intent)
                true
            } else {
                openPlayStore(context)
                false
            }
        } catch (e: Exception) {
            openPlayStore(context)
            false
        }
    }

    fun openPlayStore(context: Context) {
        try {
            val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$VN_PASS_PACKAGE")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(marketIntent)
        } catch (e: Exception) {
            val webIntent = Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=$VN_PASS_PACKAGE")).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(webIntent)
        }
    }

    fun openWebPortal(context: Context) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(WEB_PORTAL_URL)).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(context, "Không thể mở trình duyệt: $WEB_PORTAL_URL", Toast.LENGTH_SHORT).show()
        }
    }
}

object TicketPreferences {
    private const val PREFS_NAME = "allbus_ticket_prefs"
    private const val KEY_QUICK_TICKET = "pref_quick_ticket_enabled"

    fun isQuickTicketEnabled(context: Context): Boolean {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getBoolean(KEY_QUICK_TICKET, false)
    }

    fun setQuickTicketEnabled(context: Context, enabled: Boolean) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putBoolean(KEY_QUICK_TICKET, enabled)
            .apply()
    }
}

@Composable
fun TicketScreen(
    isQuickTicketEnabled: Boolean,
    onToggleQuickTicket: (Boolean) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    var isInstalled by remember { mutableStateOf(TicketAppHelper.isAppInstalled(context)) }

    // Re-check installed status whenever this screen is displayed
    LaunchedEffect(Unit) {
        isInstalled = TicketAppHelper.isAppInstalled(context)
    }

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 0.95f,
        animationSpec = infiniteRepeatable(
            animation = tween(1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseAlpha"
    )

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        // 1. Virtual Ticket Card Visual (Thẻ ảo phong cách Hà Nội Bus)
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .shadow(10.dp, shape = RoundedCornerShape(22.dp)),
            shape = RoundedCornerShape(22.dp),
            color = Color.Transparent
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        brush = Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF1B5E20), // Hanoi bus green deep
                                Color(0xFF2E7D32),
                                Color(0xFF00897B)
                            )
                        )
                    )
                    .padding(20.dp)
            ) {
                Column {
                    // Header of Card
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(Color.White.copy(alpha = 0.2f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.DirectionsBus,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                            Spacer(modifier = Modifier.width(10.dp))
                            Column {
                                Text(
                                    text = "THẺ VÉ XE BUÝT HÀ NỘI",
                                    color = Color.White,
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.ExtraBold,
                                    letterSpacing = 0.8.sp
                                )
                                Text(
                                    text = "Hanoi Virtual Transit Card",
                                    color = Color.White.copy(alpha = 0.8f),
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // App status badge
                        Surface(
                            shape = RoundedCornerShape(12.dp),
                            color = if (isInstalled) Color(0xFF00E676).copy(alpha = 0.25f) else Color(0xFFFFB300).copy(alpha = 0.3f),
                            border = androidx.compose.foundation.BorderStroke(
                                1.dp,
                                if (isInstalled) Color(0xFF00E676) else Color(0xFFFFD54F)
                            )
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(7.dp)
                                        .clip(CircleShape)
                                        .background(if (isInstalled) Color(0xFF00E676) else Color(0xFFFFD54F))
                                )
                                Spacer(modifier = Modifier.width(5.dp))
                                Text(
                                    text = if (isInstalled) "ĐÃ KẾT NỐI" else "CHƯA CÀI ĐẶT",
                                    color = Color.White,
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // QR Visual Mockup Box
                    Box(
                        modifier = Modifier
                            .align(Alignment.CenterHorizontally)
                            .size(130.dp)
                            .clip(RoundedCornerShape(16.dp))
                            .background(Color.White)
                            .padding(8.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCode2,
                                contentDescription = "Mã QR",
                                tint = Color(0xFF1B5E20),
                                modifier = Modifier.size(78.dp)
                            )
                            Spacer(modifier = Modifier.height(2.dp))
                            Text(
                                text = "MÃ QR ĐỘNG 30S",
                                color = Color(0xFF2E7D32),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                letterSpacing = 0.5.sp
                            )
                        }

                        // Glowing pulsing corner border
                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .border(
                                    width = 2.dp,
                                    color = Color(0xFF00E676).copy(alpha = pulseAlpha),
                                    shape = RoundedCornerShape(16.dp)
                                )
                        )
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    // Card Info Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(
                                text = "ĐƠN VỊ PHÁT HÀNH",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Tramoc / VNPass • Sở GTVT",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text(
                                text = "PHƯƠNG THỨC SOÁT VÉ",
                                color = Color.White.copy(alpha = 0.7f),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                            Text(
                                text = "Chạm lên / Chạm xuống",
                                color = Color(0xFFB9F6CA),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 2. Primary 1-Tap Action Button
        if (isInstalled) {
            Button(
                onClick = {
                    TicketAppHelper.openTicketApp(context)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(
                    containerColor = Color(0xFF2E7D32)
                ),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Icon(
                    imageVector = Icons.Default.QrCodeScanner,
                    contentDescription = null,
                    tint = Color.White,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = "MỞ THẺ VÉ ĐỂ QUÉT NGAY ⚡",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            Surface(
                shape = RoundedCornerShape(12.dp),
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier.padding(12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = Color(0xFF2E7D32),
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Mẹo: Sau khi đầu đọc trên xe kêu bíp xác nhận, bạn chỉ cần bấm nút Quay lại (Back) để trở về AllBus xem lộ trình tiếp theo.",
                        fontSize = 12.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        lineHeight = 16.sp
                    )
                }
            }
        } else {
            // App not installed: Guide to install
            Button(
                onClick = { TicketAppHelper.openPlayStore(context) },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE65100)),
                elevation = ButtonDefaults.buttonElevation(defaultElevation = 4.dp)
            ) {
                Icon(Icons.Default.Download, contentDescription = null, tint = Color.White)
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = "CÀI ĐẶT APP THẺ VÉ TỪ GOOGLE PLAY",
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.White
                )
            }

            Spacer(modifier = Modifier.height(8.dp))

            OutlinedButton(
                onClick = { TicketAppHelper.openWebPortal(context) },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(16.dp)
            ) {
                Icon(Icons.Default.Language, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text("Mở Cổng đăng ký vé điện tử (vedientuonline.com.vn)", fontSize = 12.sp)
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // 3. Quick Settings: Floating Shortcut Toggle
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF2E7D32).copy(alpha = 0.12f)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.QrCodeScanner,
                                contentDescription = null,
                                tint = Color(0xFF2E7D32),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Nút quét vé nhanh",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                text = "Bật phím tắt quét QR trên thanh tiêu đề và nút nổi",
                                fontSize = 11.sp,
                                color = MaterialTheme.colorScheme.outline
                            )
                        }
                    }
                    Switch(
                        checked = isQuickTicketEnabled,
                        onCheckedChange = { onToggleQuickTicket(it) }
                    )
                }

                if (isQuickTicketEnabled) {
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "✨ Nút quét vé QR sẽ hiển thị ở góc trên thanh tiêu đề và nút nổi ở góc dưới màn hình giúp bạn mở vé nhanh chóng chỉ với 1 chạm.",
                        fontSize = 11.sp,
                        color = Color(0xFF2E7D32),
                        lineHeight = 15.sp
                    )
                }
            }
        }

        Spacer(modifier = Modifier.height(18.dp))

        // 4. Instructions and Guidelines
        Text(
            text = "Hướng dẫn & Quy định Thẻ vé ảo",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.fillMaxWidth().padding(start = 4.dp, bottom = 8.dp)
        )

        val tips = listOf(
            Triple(
                Icons.Default.Timer,
                "Mã QR tự động đổi sau 30 giây",
                "Mã QR chứa chữ ký mã hóa và tự động thay đổi sau mỗi 30s. Bạn không thể chụp ảnh màn hình để dùng lại hoặc chia sẻ cho người khác."
            ),
            Triple(
                Icons.Default.SyncAlt,
                "Quy tắc Chạm lên & Chạm xuống",
                "Đối với vé lượt, hành khách cần quét mã QR cả khi lên xe và khi xuống xe để hệ thống ghi nhận đúng số km và khấu trừ đúng mức giá cước."
            ),
            Triple(
                Icons.Default.Badge,
                "Đăng ký thẻ ảo qua CCCD",
                "Người dùng có thể đăng ký tài khoản và định danh bằng CCCD gắn chip trong ứng dụng Thẻ vé giao thông HN để phát hành thẻ ảo miễn phí."
            ),
            Triple(
                Icons.Default.Train,
                "Liên thông Xe buýt & Metro",
                "Mã QR từ hệ thống dùng được trên toàn bộ xe buýt trợ giá Hà Nội (Transerco, VinBus, Bảo Yến) và các tuyến Đường sắt đô thị (Cát Linh - Hà Đông, Nhổn - Ga Hà Nội)."
            )
        )

        tips.forEach { (icon, title, desc) ->
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 4.dp),
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f)
                )
            ) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.Top) {
                    Box(
                        modifier = Modifier
                            .size(30.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF2E7D32).copy(alpha = 0.1f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(icon, contentDescription = null, tint = Color(0xFF2E7D32), modifier = Modifier.size(16.dp))
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Column {
                        Text(text = title, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = desc,
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            lineHeight = 15.sp
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Version badge footer
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ) {
            Text(
                text = "AllBus v1.0 (Build 1) • Tra cứu xe buýt Hà Nội",
                fontSize = 11.sp,
                fontWeight = FontWeight.Medium,
                color = MaterialTheme.colorScheme.outline,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
            )
        }

        Spacer(modifier = Modifier.height(24.dp))
    }
}

/**
 * Floating Quick Action Button that appears at bottom right for 1-tap QR scanning
 */
@Composable
fun FloatingTicketButton(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Surface(
        onClick = onClick,
        modifier = modifier
            .size(54.dp)
            .shadow(8.dp, shape = CircleShape),
        shape = CircleShape,
        color = Color(0xFF1B5E20),
        border = androidx.compose.foundation.BorderStroke(2.dp, Color(0xFF00E676))
    ) {
        Box(contentAlignment = Alignment.Center) {
            Icon(
                imageVector = Icons.Default.QrCodeScanner,
                contentDescription = "Mở thẻ vé quét QR",
                tint = Color.White,
                modifier = Modifier.size(28.dp)
            )
        }
    }
}
