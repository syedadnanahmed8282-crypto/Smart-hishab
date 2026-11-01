package com.smarthisab.app.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.navigation.NavHostController

@Composable
private fun LogoBadge(modifier: Modifier = Modifier) {
    val bob by rememberInfiniteTransition(label = "bob").animateFloat(0f, -6f, infiniteRepeatable(tween(1500), RepeatMode.Reverse), label = "b")
    Box(
        modifier.offset(y = bob.dp).size(84.dp).shadow(18.dp, RoundedCornerShape(26.dp)).clip(RoundedCornerShape(26.dp)).background(BtnBrush),
        contentAlignment = Alignment.Center,
    ) { Text("৳", color = Color.White, fontSize = 44.sp, fontWeight = FontWeight.Bold) }
}

@Composable
private fun AuthShell(content: @Composable ColumnScope.() -> Unit) {
    Box(Modifier.fillMaxSize().background(Sky50)) {
        HeroBackdrop(Modifier.fillMaxWidth().height(300.dp))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Spacer(Modifier.height(56.dp))
            LogoBadge()
            Text("স্মার্ট হিসাব", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Navy, modifier = Modifier.padding(top = 12.dp))
            Text("দৈনিক কাজের হিসাব, সবসময় হাতের কাছে", fontSize = 13.sp, color = Color(0xFF0C4A6E))
            Spacer(Modifier.height(28.dp))
            Column(
                Modifier.fillMaxWidth().shadow(14.dp, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp)).background(Color.White, RoundedCornerShape(topStart = 32.dp, topEnd = 32.dp))
                    .padding(horizontal = 20.dp, vertical = 24.dp).heightIn(min = 520.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp), content = content,
            )
        }
    }
}

@Composable
fun LoginScreen(vm: AppVm, nav: NavHostController) {
    var email by rememberSaveable { mutableStateOf("") }
    var pw by rememberSaveable { mutableStateOf("") }
    AuthShell {
        Text("লগইন", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text("আপনার হিসাব ক্লাউড থেকে ফিরিয়ে আনুন", fontSize = 13.sp, color = Slate)
        Field("ইমেইল", email, { email = it })
        Field("পাসওয়ার্ড", pw, { pw = it }, password = true)
        TextButton({ nav.navigate("forgot") }, Modifier.align(Alignment.End)) { Text("পাসওয়ার্ড ভুলে গেছেন?", fontWeight = FontWeight.SemiBold) }
        PrimaryButton("লগইন", Modifier.fillMaxWidth(), enabled = email.isNotBlank() && pw.length >= 6) { vm.login(email, pw) }
        Row(verticalAlignment = Alignment.CenterVertically) {
            HorizontalDivider(Modifier.weight(1f), color = Sky100); Text("  অথবা  ", fontSize = 12.sp, color = Slate); HorizontalDivider(Modifier.weight(1f), color = Sky100)
        }
        SoftButton("G   Google দিয়ে চালিয়ে যান", Modifier.fillMaxWidth(), color = Navy) { vm.google() }
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            Text("নতুন ব্যবহারকারী?", color = Slate, fontSize = 14.sp)
            TextButton({ nav.navigate("register") }) { Text("অ্যাকাউন্ট খুলুন", fontWeight = FontWeight.Bold) }
        }
        TextButton({ vm.continueAsGuest() }, Modifier.align(Alignment.CenterHorizontally)) {
            Text("লগইন ছাড়া চালিয়ে যান (শুধু এই ফোনে থাকবে)", fontSize = 12.sp, color = Slate)
        }
    }
}

@Composable
fun RegisterScreen(vm: AppVm, nav: NavHostController) {
    var name by rememberSaveable { mutableStateOf("") }
    var email by rememberSaveable { mutableStateOf("") }
    var pw by rememberSaveable { mutableStateOf("") }
    var pw2 by rememberSaveable { mutableStateOf("") }
    var agree by rememberSaveable { mutableStateOf(true) }
    var sent by rememberSaveable { mutableStateOf(false) }
    val score = (if (pw.length >= 6) 1 else 0) + (if (pw.length >= 8) 1 else 0) + (if (pw.any { it.isDigit() } && pw.any { it.isLetter() }) 1 else 0)
    AuthShell {
        if (sent) {
            Text("ইমেইল যাচাই করুন", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Navy)
            Text("$email ঠিকানায় একটা লিংক পাঠিয়েছি। লিংকে চাপলে অ্যাকাউন্ট চালু হবে। না পেলে স্প্যাম ফোল্ডার দেখুন।", color = Slate, fontSize = 14.sp)
            PrimaryButton("লগইনে ফিরে যান", Modifier.fillMaxWidth()) { nav.navigate("login") { popUpTo("login") { inclusive = true } } }
        } else {
            Text("নতুন অ্যাকাউন্ট", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Navy)
            Field("আপনার নাম", name, { name = it })
            Field("ইমেইল", email, { email = it })
            Field("পাসওয়ার্ড (কমপক্ষে ৬ অক্ষর)", pw, { pw = it }, password = true)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                repeat(3) { Box(Modifier.weight(1f).height(6.dp).clip(CircleShape).background(if (it < score) Sky500 else Sky100)) }
            }
            Text(listOf("দুর্বল", "দুর্বল", "মাঝারি", "শক্তিশালী")[score], fontSize = 12.sp, color = if (score >= 3) Green else Amber)
            Field("পাসওয়ার্ড আবার দিন", pw2, { pw2 = it }, password = true)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(agree, { agree = it }); Text("আমি শর্তাবলী ও গোপনীয়তা নীতি মেনে নিচ্ছি", fontSize = 13.sp, color = Slate)
            }
            val ok = name.isNotBlank() && email.contains("@") && pw.length >= 6 && pw == pw2 && agree
            PrimaryButton("অ্যাকাউন্ট খুলুন", Modifier.fillMaxWidth(), enabled = ok) {
                vm.register(name, email, pw) { needsConfirm -> if (needsConfirm) sent = true }
            }
            if (pw2.isNotEmpty() && pw != pw2) Text("পাসওয়ার্ড দুটো মিলছে না", color = Rose, fontSize = 12.sp)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
                Text("আগে থেকেই অ্যাকাউন্ট আছে?", color = Slate, fontSize = 14.sp)
                TextButton({ nav.popBackStack() }) { Text("লগইন", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
fun ForgotScreen(vm: AppVm, nav: NavHostController) {
    var email by rememberSaveable { mutableStateOf("") }
    var sent by rememberSaveable { mutableStateOf(false) }
    AuthShell {
        Text("পাসওয়ার্ড রিসেট", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text(if (sent) "$email ঠিকানায় রিসেট লিংক পাঠানো হয়েছে।" else "আপনার ইমেইল দিন। পাসওয়ার্ড নতুন করে দেওয়ার একটা লিংক পাঠাব।", color = Slate, fontSize = 14.sp)
        if (!sent) {
            Field("ইমেইল", email, { email = it })
            PrimaryButton("রিসেট লিংক পাঠান", Modifier.fillMaxWidth(), enabled = email.contains("@")) { vm.resetPassword(email) { sent = true } }
        }
        TextButton({ nav.popBackStack() }, Modifier.align(Alignment.CenterHorizontally)) { Text("লগইনে ফিরে যান", fontWeight = FontWeight.SemiBold) }
    }
}
