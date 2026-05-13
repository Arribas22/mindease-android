package com.bravosix.mindease.ui

import android.app.Activity
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CardGiftcard
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.ShoppingCart
import androidx.compose.material.icons.filled.Token
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.bravosix.mindease.BillingManager
import com.bravosix.mindease.Prefs
import com.bravosix.mindease.RewardedAdManager

@Composable
fun TokensScreen() {
    val ctx           = LocalContext.current
    val activity      = ctx as Activity
    val billing       = remember { BillingManager.get() }
    val isPremium     = Prefs.isPremium(ctx)

    // Refresh on every recomposition (tokens change after ads/purchases)
    val totalTokens     = Prefs.totalTokens(ctx)
    val dailyRemaining  = Prefs.dailyTokensRemaining(ctx)
    val extraBalance    = Prefs.tokenBalance(ctx)

    val lifetimePrice   by billing.lifetimeDetails.collectAsState()
    val tokens50Price   by billing.tokens50Details.collectAsState()
    val tokens200Price  by billing.tokens200Details.collectAsState()

    var rewardMessage by remember { mutableStateOf<String?>(null) }
    var refreshKey    by remember { mutableIntStateOf(0) }  // force recomposition after reward

    // Re-read tokens after reward
    val currentTokens = remember(refreshKey) { Prefs.totalTokens(ctx) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        // ── Header ──────────────────────────────────────────────────────────
        Text(
            "Mi cuenta",
            style      = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.Bold
        )

        // ── Token balance card ──────────────────────────────────────────────
        if (isPremium) {
            PremiumBadgeCard()
        } else {
            TokenBalanceCard(
                dailyRemaining = dailyRemaining,
                extraBalance   = extraBalance,
                total          = currentTokens
            )
        }

        // ── Earn tokens: watch a video ──────────────────────────────────────
        if (!isPremium) {
            SectionTitle("Ganar tokens gratis")
            EarnTokensCard(
                tokensPerAd = Prefs.TOKENS_PER_AD,
                isAdReady   = RewardedAdManager.isReady(),
                onWatch = {
                    RewardedAdManager.show(
                        activity     = activity,
                        tokensAmount = Prefs.TOKENS_PER_AD,
                        onReward     = { added ->
                            rewardMessage = "+$added tokens añadidos 🎉"
                            refreshKey++
                        },
                        onUnavailable = {
                            rewardMessage = "Anuncio no disponible, inténtalo en un momento"
                        }
                    )
                }
            )
            rewardMessage?.let { msg ->
                Text(
                    msg,
                    style  = MaterialTheme.typography.bodySmall,
                    color  = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.fillMaxWidth(),
                    textAlign = TextAlign.Center
                )
            }
        }

        // ── Buy token packs ─────────────────────────────────────────────────
        if (!isPremium) {
            SectionTitle("Comprar tokens")
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                TokenPackCard(
                    modifier    = Modifier.weight(1f),
                    tokens      = 50,
                    price       = tokens50Price?.oneTimePurchaseOfferDetails?.formattedPrice ?: "0,99 €",
                    highlighted = false,
                    onClick     = { billing.launchTokens50(activity) }
                )
                TokenPackCard(
                    modifier    = Modifier.weight(1f),
                    tokens      = 200,
                    price       = tokens200Price?.oneTimePurchaseOfferDetails?.formattedPrice ?: "2,99 €",
                    highlighted = true,
                    onClick     = { billing.launchTokens200(activity) }
                )
            }
        }

        // ── Lifetime plan ───────────────────────────────────────────────────
        SectionTitle(if (isPremium) "Tu plan" else "Plan Vitalicio")
        LifetimeCard(
            isPremium    = isPremium,
            price        = lifetimePrice?.oneTimePurchaseOfferDetails?.formattedPrice ?: "4,99 €",
            onBuy        = { billing.launchLifetime(activity) }
        )

        // ── Support tip ─────────────────────────────────────────────────────
        if (!isPremium) {
            SectionTitle("Apoya el proyecto")
            SupportCard(
                price   = "0,99 €",
                onClick = { billing.launchTip(activity) }
            )
        }

        Spacer(Modifier.height(8.dp))
    }
}

// ── Sub-composables ──────────────────────────────────────────────────────────

@Composable
private fun SectionTitle(title: String) {
    Text(
        title,
        style      = MaterialTheme.typography.titleSmall,
        fontWeight = FontWeight.SemiBold,
        color      = MaterialTheme.colorScheme.onSurfaceVariant
    )
}

@Composable
private fun PremiumBadgeCard() {
    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
    ) {
        Row(
            Modifier.padding(20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Icon(
                Icons.Default.AutoAwesome,
                contentDescription = null,
                tint   = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(40.dp)
            )
            Column {
                Text("Plan Vitalicio activo ✨", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                Text("Tokens ilimitados para siempre. Sin anuncios.", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun TokenBalanceCard(dailyRemaining: Int, extraBalance: Int, total: Int) {
    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Default.Token, null, tint = MaterialTheme.colorScheme.secondary)
                Text("Tus tokens de chat", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceEvenly
            ) {
                TokenStat("$dailyRemaining", "Diarios hoy")
                VerticalDivider(Modifier.height(40.dp))
                TokenStat("$extraBalance", "Extra")
                VerticalDivider(Modifier.height(40.dp))
                TokenStat(if (total > 999) "∞" else "$total", "Total")
            }
            Text(
                "Cada mensaje con IA consume 1 token. Los diarios se reponen cada día.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSecondaryContainer
            )
        }
    }
}

@Composable
private fun TokenStat(value: String, label: String) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(value, fontSize = 28.sp, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
    }
}

@Composable
private fun EarnTokensCard(tokensPerAd: Int, isAdReady: Boolean, onWatch: () -> Unit) {
    Card(shape = RoundedCornerShape(16.dp)) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.CardGiftcard, null, Modifier.size(32.dp), tint = MaterialTheme.colorScheme.primary)
            Column(Modifier.weight(1f)) {
                Text("Ver un vídeo corto", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("+$tokensPerAd tokens gratis al completarlo", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Button(
                onClick  = onWatch,
                enabled  = isAdReady,
                modifier = Modifier.wrapContentWidth()
            ) {
                Text(if (isAdReady) "Ver" else "Cargando…")
            }
        }
    }
}

@Composable
private fun TokenPackCard(
    modifier: Modifier,
    tokens: Int,
    price: String,
    highlighted: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier,
        shape    = RoundedCornerShape(16.dp),
        colors   = CardDefaults.cardColors(
            containerColor = if (highlighted)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surfaceVariant
        )
    ) {
        Column(
            Modifier.padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            if (highlighted) {
                Text("POPULAR", style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
            }
            Text("$tokens tokens", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            Text(price, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Button(onClick = onClick, modifier = Modifier.fillMaxWidth()) { Text("Comprar") }
        }
    }
}

@Composable
private fun LifetimeCard(isPremium: Boolean, price: String, onBuy: () -> Unit) {
    Card(
        shape  = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = if (isPremium)
                MaterialTheme.colorScheme.primaryContainer
            else
                MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Default.AutoAwesome, null, tint = MaterialTheme.colorScheme.primary)
                Text("Vitalicio — Tokens ilimitados", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                FeatureLine("✅ Tokens de chat ilimitados para siempre")
                FeatureLine("✅ Sin anuncios en la app")
                FeatureLine("✅ Acceso a todas las funciones futuras")
                FeatureLine("✅ 1 pago único, sin suscripción")
            }
            if (isPremium) {
                Text("Activo en tu cuenta ✨", style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.SemiBold)
            } else {
                Button(
                    onClick  = onBuy,
                    modifier = Modifier.fillMaxWidth(),
                    colors   = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.ShoppingCart, null, Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Obtener por $price")
                }
            }
        }
    }
}

@Composable
private fun SupportCard(price: String, onClick: () -> Unit) {
    OutlinedCard(shape = RoundedCornerShape(16.dp)) {
        Row(
            Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(Icons.Default.Favorite, null, Modifier.size(28.dp), tint = MaterialTheme.colorScheme.error)
            Column(Modifier.weight(1f)) {
                Text("Apoyar al desarrollador", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                Text("$price · Donación única para seguir mejorando MindEase", style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            OutlinedButton(onClick = onClick) { Text("❤️ $price") }
        }
    }
}

@Composable
private fun FeatureLine(text: String) {
    Text(text, style = MaterialTheme.typography.bodySmall)
}
