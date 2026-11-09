package com.bob.treasury

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBalance
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CompareArrows
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Navy = Color(0xFF10213B)
private val Teal = Color(0xFF12A995)
private val Pale = Color(0xFFF3F6FA)
private val Ink = Color(0xFF17243A)
private val Muted = Color(0xFF66758A)
private const val TAX_RATE = 0.20

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { TreasuryApp(this) }
    }
}

private data class PurchaseResult(val factor: Double, val debit: Double, val gross: Double, val tax: Double, val net: Double, val maturity: Double)
private data class RedemptionResult(val purchase: PurchaseResult, val redemptionFactor: Double, val proceeds: Double, val gain: Double, val tax: Double, val netGain: Double, val finalAmount: Double)
private fun num(s: String) = s.trim().replace(',', '.').toDouble()
private fun purchase(amount: Double, days: Double, rate: Double): PurchaseResult {
    require(amount > 0 && days > 0 && rate >= 0)
    val factor = 1.0 / (1.0 + days / 365.0 * rate / 100.0)
    val debit = factor * amount
    val gross = amount - debit
    val tax = gross * TAX_RATE
    return PurchaseResult(factor, debit, gross, tax, gross - tax, amount - tax)
}
// Matches the workbook's "إسترداد" sheet: elapsed days are deducted from original term.
private fun redemption(amount: Double, termDays: Double, purchaseRate: Double, elapsedDays: Double, exitRate: Double): RedemptionResult {
    require(amount > 0 && termDays > 0 && elapsedDays >= 0 && elapsedDays <= termDays && purchaseRate >= 0 && exitRate >= 0)
    val p = purchase(amount, termDays, purchaseRate)
    val remaining = termDays - elapsedDays
    val factor = 1.0 / (1.0 + exitRate / 100.0 * remaining / 365.0)
    val proceeds = amount * factor
    val gain = proceeds - p.debit
    val tax = if (gain < 0) 0.0 else gain * TAX_RATE
    val net = gain - tax
    val finalAmount = amount - (p.gross - gain)
    return RedemptionResult(p, factor, proceeds, gain, tax, net, finalAmount)
}
private fun money(v: Double) = DecimalFormat("#,##0.00", java.text.DecimalFormatSymbols(Locale.US)).format(v) + " ج.م"
private fun pct(v: Double) = DecimalFormat("0.000", java.text.DecimalFormatSymbols(Locale.US)).format(v) + "%"

@Composable private fun TreasuryApp(context: Context) {
    var page by remember { mutableIntStateOf(0) }
    var amount by remember { mutableStateOf("100000") }
    var days by remember { mutableStateOf("91") }
    var rate by remember { mutableStateOf("24") }
    var elapsed by remember { mutableStateOf("30") }
    var exitRate by remember { mutableStateOf("24") }
    var result by remember { mutableStateOf<PurchaseResult?>(null) }
    var redemptionResult by remember { mutableStateOf<RedemptionResult?>(null) }
    var error by remember { mutableStateOf("") }
    var history by remember { mutableStateOf(loadHistory(context)) }
    var auctions by remember { mutableStateOf(loadAuctions(context)) }
    var isin by remember { mutableStateOf("") }
    var tenor by remember { mutableStateOf("91") }
    var auctionDate by remember { mutableStateOf("") }
    var issueDate by remember { mutableStateOf("") }
    var maturityDate by remember { mutableStateOf("") }
    var avgRate by remember { mutableStateOf("") }
    var minRate by remember { mutableStateOf("") }
    var maxRate by remember { mutableStateOf("") }
    var accepted by remember { mutableStateOf("") }
    var auctionError by remember { mutableStateOf("") }
    MaterialTheme(colorScheme = lightColorScheme(primary = Navy, secondary = Teal, background = Pale, surface = Color.White)) {
        Scaffold(containerColor = Pale, bottomBar = {
            NavigationBar(containerColor = Color.White) {
                NavigationBarItem(selected = page == 0, onClick = { page = 0; error = "" }, icon = { Icon(Icons.Default.Calculate, null) }, label = { Text("شراء") })
                NavigationBarItem(selected = page == 1, onClick = { page = 1; error = "" }, icon = { Icon(Icons.Default.AccountBalance, null) }, label = { Text("استرداد") })
                NavigationBarItem(selected = page == 2, onClick = { page = 2; error = "" }, icon = { Icon(Icons.Default.CompareArrows, null) }, label = { Text("مقارنة") })
                NavigationBarItem(selected = page == 3, onClick = { page = 3; error = "" }, icon = { Icon(Icons.Default.History, null) }, label = { Text("السجل") })
                NavigationBarItem(selected = page == 4, onClick = { page = 4; error = "" }, icon = { Icon(Icons.Default.Edit, null) }, label = { Text("الطروحات") })
            }
        }) { padding ->
            Column(Modifier.fillMaxSize().padding(padding)) {
                Header()
                when (page) {
                    0 -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        PageTitle("حساب شراء إذن", "احسب تكلفة الشراء وصافي العائد حتى الاستحقاق")
                        InputCard {
                            NumberField("القيمة الاسمية (جنيه)", amount) { amount = it }
                            NumberField("مدة الإذن (يوم)", days) { days = it }
                            NumberField("العائد السنوي للطرح الأولي (%)", rate) { rate = it }
                            if (auctions.isNotEmpty()) {
                                Text("اختيار عائد من طرح محفوظ", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                                auctions.sortedByDescending { it.auctionDate }.take(8).forEach { a ->
                                    TextButton(onClick = { days = a.tenor.toString(); rate = a.average.toString(); error = "" }, modifier = Modifier.fillMaxWidth()) {
                                        Text("${a.tenor} يوم • ${a.isin} • ${pct(a.average)} • ${a.auctionDate}", color = Teal, textAlign = TextAlign.Start)
                                    }
                                }
                            }
                            Button(onClick = { runCatching { purchase(num(amount), num(days), num(rate)) }.onSuccess { result = it; redemptionResult = null; error = ""; history = addHistory(context, "شراء", amount, days, rate, money(it.net)) }.onFailure { error = "تحقق من المدخلات: القيمة والمدة أكبر من صفر والعائد لا يقل عن صفر." } }, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Teal), shape = RoundedCornerShape(14.dp)) { Text("احسب واحفظ العملية", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                        }
                        ErrorText(error)
                        result?.let { PurchaseResults(it) }
                        Disclaimer()
                    }
                    1 -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        PageTitle("حساب الاسترداد", "احسب قيمة البيع قبل الاستحقاق وفق الأيام المتبقية")
                        InputCard {
                            NumberField("القيمة الاسمية (جنيه)", amount) { amount = it }
                            NumberField("مدة الإذن الأصلية (يوم)", days) { days = it }
                            NumberField("عائد الشراء السنوي (%)", rate) { rate = it }
                            NumberField("الأيام المنقضية منذ الشراء", elapsed) { elapsed = it }
                            NumberField("عائد الاسترداد السنوي (%)", exitRate) { exitRate = it }
                            Button(onClick = { runCatching { redemption(num(amount), num(days), num(rate), num(elapsed), num(exitRate)) }.onSuccess { redemptionResult = it; result = null; error = ""; history = addHistory(context, "استرداد", amount, days, rate, money(it.netGain)) }.onFailure { error = "راجع المدخلات. الأيام المنقضية لا تتجاوز مدة الإذن." } }, modifier = Modifier.fillMaxWidth().height(52.dp), colors = ButtonDefaults.buttonColors(containerColor = Teal), shape = RoundedCornerShape(14.dp)) { Text("احسب الاسترداد واحفظه", fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                        }
                        ErrorText(error)
                        redemptionResult?.let { RedemptionResults(it, numSafe(elapsed), numSafe(days)) }
                        Disclaimer()
                    }
                    2 -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        PageTitle("مقارنة آجال الأذون", "مقارنة مستقلة باستخدام نفس العائد المدخل للطرح الأولي")
                        InputCard {
                            NumberField("القيمة الاسمية (جنيه)", amount) { amount = it }
                            NumberField("العائد السنوي (%)", rate) { rate = it }
                            Text("المقارنة التالية حسابية على العائد الذي تدخله؛ لا تمثل أسعار مزاد مباشرة ما لم تُحدّثها من نتيجة المزاد الرسمية.", color = Muted, fontSize = 12.sp)
                        }
                        val a = numSafe(amount); val r = numSafe(rate)
                        if (a > 0 && r >= 0) Card(Modifier.padding(16.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                listOf(91, 182, 273, 364).forEach { d ->
                                    val x = runCatching { purchase(a, d.toDouble(), r) }.getOrNull()
                                    if (x != null) Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                                        Column { Text("$d يوم", color = Ink, fontWeight = FontWeight.Bold); Text("صافي العائد ${money(x.net)}", color = Muted, fontSize = 11.sp) }
                                        Text("تكلفة الشراء ${money(x.debit)}", color = Teal, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    }
                                    if (d != 364) HorizontalDivider(color = Pale)
                                }
                            }
                        }
                        Disclaimer()
                    }
                    3 -> Column(Modifier.weight(1f)) {
                        PageTitle("سجل العمليات", "العمليات محفوظة محليًا على هذا الهاتف")
                        if (history.isEmpty()) InfoCard("لا توجد عمليات محفوظة", "قم بإجراء عملية شراء أو استرداد لحفظها هنا.")
                        else {
                            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp), horizontalArrangement = Arrangement.End) { TextButton(onClick = { clearHistory(context); history = emptyList() }) { Text("مسح السجل", color = Color(0xFFB42318)) } }
                            LazyColumn(contentPadding = PaddingValues(horizontal = 16.dp, vertical = 4.dp), verticalArrangement = Arrangement.spacedBy(9.dp)) {
                                items(history.reversed()) { item -> Card(colors = CardDefaults.cardColors(containerColor = Color.White), modifier = Modifier.fillMaxWidth()) { Column(Modifier.padding(14.dp)) { Text(item.substringBefore("|"), color = Ink, fontWeight = FontWeight.Bold); Text(item.substringAfter("|"), color = Muted, fontSize = 12.sp) } } }
                            }
                        }
                    }
                    else -> Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
                        PageTitle("إدارة طروحات البنك المركزي", "إدخال يدوي لبيانات الطرح الأولي فقط؛ لا تُستخدم بيانات السوق الثانوي")
                        InputCard {
                            TextEntryField("كود الإذن ISIN", isin) { isin = it.uppercase() }
                            Text("مدة الإذن", color = Ink, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf("91", "182", "273", "364").forEach { d ->
                                    FilterChip(selected = tenor == d, onClick = { tenor = d }, label = { Text("$d يوم") })
                                }
                            }
                            TextEntryField("تاريخ المزاد (YYYY-MM-DD)", auctionDate) { auctionDate = it }
                            TextEntryField("تاريخ الإصدار (YYYY-MM-DD)", issueDate) { issueDate = it }
                            TextEntryField("تاريخ الاستحقاق (YYYY-MM-DD)", maturityDate) { maturityDate = it }
                            NumberField("متوسط العائد المرجح (%)", avgRate) { avgRate = it }
                            NumberField("أقل عائد مقبول (%)", minRate) { minRate = it }
                            NumberField("أعلى عائد مقبول (%)", maxRate) { maxRate = it }
                            NumberField("قيمة العطاءات المقبولة (جنيه - اختياري)", accepted) { accepted = it }
                            Button(onClick = {
                                val a = numSafe(avgRate); val lo = numSafe(minRate); val hi = numSafe(maxRate)
                                val validDate = Regex("\\d{4}-\\d{2}-\\d{2}")
                                if (isin.isBlank() || !validDate.matches(auctionDate) || !validDate.matches(issueDate) || !validDate.matches(maturityDate) || avgRate.isBlank() || a <= 0 || lo < 0 || hi < lo || a !in lo..hi) {
                                    auctionError = "راجع الكود والتواريخ والعوائد؛ المتوسط يجب أن يقع بين الحدين الأدنى والأعلى."
                                } else {
                                    val item = Auction(isin.trim(), tenor.toInt(), auctionDate, issueDate, maturityDate, a, lo, hi, accepted.trim())
                                    auctions = (auctions.filterNot { it.isin.equals(item.isin, true) } + item).sortedByDescending { it.auctionDate }
                                    saveAuctions(context, auctions); auctionError = "تم حفظ الطرح بنجاح على الهاتف."; rate = a.toString(); days = tenor
                                }
                            }, modifier = Modifier.fillMaxWidth().height(50.dp), colors = ButtonDefaults.buttonColors(containerColor = Teal), shape = RoundedCornerShape(14.dp)) { Text("حفظ الطرح") }
                            if (auctionError.isNotBlank()) Text(auctionError, color = if (auctionError.startsWith("تم")) Teal else Color(0xFFB42318), fontSize = 12.sp)
                        }
                        PageTitle("الطروحات المحفوظة (${auctions.size})", "اضغط على طرح لاستخدام متوسط عائده في شاشة الشراء")
                        if (auctions.isEmpty()) InfoCard("لا توجد طروحات بعد", "أدخل بيانات أول مزاد من هذه الشاشة.")
                        auctions.forEach { a ->
                            Card(Modifier.padding(horizontal = 16.dp, vertical = 4.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                                Column(Modifier.padding(14.dp)) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Column(Modifier.weight(1f)) { Text(a.isin, color = Ink, fontWeight = FontWeight.Bold); Text("${a.tenor} يوم • مزاد ${a.auctionDate}", color = Muted, fontSize = 11.sp) }
                                        IconButton(onClick = { auctions = auctions.filterNot { it.isin == a.isin }; saveAuctions(context, auctions); if (auctions.isEmpty()) auctionError = "" }) { Icon(Icons.Default.Delete, "حذف", tint = Color(0xFFB42318)) }
                                    }
                                    Text("متوسط ${pct(a.average)}  |  أدنى ${pct(a.minimum)}  |  أعلى ${pct(a.maximum)}", color = Teal, fontWeight = FontWeight.SemiBold, fontSize = 12.sp)
                                    Text("إصدار ${a.issueDate} • استحقاق ${a.maturityDate}", color = Muted, fontSize = 11.sp)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        TextButton(onClick = { days = a.tenor.toString(); rate = a.average.toString(); page = 0 }) { Text("استخدام العائد") }
                                        TextButton(onClick = { isin = a.isin; tenor = a.tenor.toString(); auctionDate = a.auctionDate; issueDate = a.issueDate; maturityDate = a.maturityDate; avgRate = a.average.toString(); minRate = a.minimum.toString(); maxRate = a.maximum.toString(); accepted = a.accepted; auctionError = "تم تحميل بيانات الطرح للتعديل؛ احفظ بعد التعديل." }) { Text("تعديل البيانات") }
                                    }
                                }
                            }
                        }
                        Disclaimer()
                    }
                }
            }
        }
    }
}


private data class Auction(
    val isin: String, val tenor: Int, val auctionDate: String, val issueDate: String,
    val maturityDate: String, val average: Double, val minimum: Double,
    val maximum: Double, val accepted: String
)
private fun loadAuctions(c: Context): List<Auction> = c.getSharedPreferences("treasury", Context.MODE_PRIVATE)
    .getString("auctions", "").orEmpty().lineSequence().filter { it.isNotBlank() }.mapNotNull { line ->
        runCatching {
            val p = line.split("~")
            Auction(p[0], p[1].toInt(), p[2], p[3], p[4], p[5].toDouble(), p[6].toDouble(), p[7].toDouble(), p.getOrElse(8) { "" })
        }.getOrNull()
    }.toList()
private fun saveAuctions(c: Context, list: List<Auction>) {
    val data = list.joinToString("\n") { listOf(it.isin, it.tenor, it.auctionDate, it.issueDate, it.maturityDate, it.average, it.minimum, it.maximum, it.accepted).joinToString("~") }
    c.getSharedPreferences("treasury", Context.MODE_PRIVATE).edit().putString("auctions", data).apply()
}
private fun loadHistory(c: Context): List<String> = c.getSharedPreferences("treasury", Context.MODE_PRIVATE).getString("history", "")!!.split("\n").filter { it.isNotBlank() }
private fun addHistory(c: Context, type: String, amount: String, days: String, rate: String, outcome: String): List<String> {
    val stamp = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale("ar", "EG")).format(Date())
    val old = loadHistory(c)
    val updated = (old + "$type|$stamp • قيمة اسمية $amount • $days يوم • عائد $rate% • النتيجة $outcome").takeLast(100)
    c.getSharedPreferences("treasury", Context.MODE_PRIVATE).edit().putString("history", updated.joinToString("\n")).apply()
    return updated
}
private fun clearHistory(c: Context) { c.getSharedPreferences("treasury", Context.MODE_PRIVATE).edit().remove("history").apply() }
private fun numSafe(s: String) = runCatching { num(s) }.getOrDefault(0.0)

@Composable private fun Header() {
    Column(Modifier.fillMaxWidth().background(Navy).padding(horizontal = 20.dp, vertical = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.AccountBalance, null, tint = Teal, modifier = Modifier.size(30.dp)); Spacer(Modifier.width(10.dp)); Text("حاسبة الأذون المتطور", color = Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold) }
        Text("حاسبة مالية لأذون الخزانة المصرية", color = Color(0xFFB8C5D8), fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp))
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) { Surface(color = Color(0xFF193D4A), shape = RoundedCornerShape(50)) { Text("الطرح الأولي فقط", color = Color(0xFF7DE4D8), modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp), fontSize = 11.sp) }; Spacer(Modifier.weight(1f)); Text("البيانات الرسمية: إدخال العائد يدويًا حاليًا", color = Color(0xFFB8C5D8), fontSize = 10.sp) }
    }
}
@Composable private fun PageTitle(title: String, subtitle: String) { Column(Modifier.padding(horizontal = 18.dp, vertical = 14.dp)) { Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, color = Ink); Text(subtitle, fontSize = 12.sp, color = Muted, modifier = Modifier.padding(top = 4.dp)) } }
@Composable private fun InputCard(content: @Composable ColumnScope.() -> Unit) { Column(Modifier.padding(horizontal = 16.dp).fillMaxWidth().background(Color.White, RoundedCornerShape(20.dp)).padding(18.dp), verticalArrangement = Arrangement.spacedBy(12.dp), content = content) }
@Composable private fun NumberField(label: String, value: String, onValue: (String) -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(label, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); OutlinedTextField(value = value, onValueChange = { onValue(it.filter { c -> c.isDigit() || c == '.' || c == ',' }) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Teal, unfocusedBorderColor = Color(0xFFDCE3EC))) } }
@Composable private fun TextEntryField(label: String, value: String, onValue: (String) -> Unit) { Column(verticalArrangement = Arrangement.spacedBy(5.dp)) { Text(label, color = Ink, fontSize = 13.sp, fontWeight = FontWeight.SemiBold); OutlinedTextField(value = value, onValueChange = onValue, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii), shape = RoundedCornerShape(12.dp), colors = OutlinedTextFieldDefaults.colors(focusedBorderColor = Teal, unfocusedBorderColor = Color(0xFFDCE3EC))) } }
@Composable private fun ErrorText(s: String) { if (s.isNotBlank()) Text(s, color = Color(0xFFB42318), modifier = Modifier.padding(16.dp), fontSize = 12.sp) }
@Composable private fun PurchaseResults(r: PurchaseResult) { ResultCard("نتيجة شراء الإذن", "صافي العائد", r.net) { ResultLine("معامل القيمة الحالية", DecimalFormat("0.000000").format(r.factor)); ResultLine("المبلغ المخصوم من الحساب", money(r.debit)); ResultLine("إجمالي العائد", money(r.gross)); ResultLine("الضريبة (20% حسب ملف Excel)", money(r.tax)); ResultLine("المبلغ آخر المدة", money(r.maturity)) } }
@Composable private fun RedemptionResults(r: RedemptionResult, elapsed: Double, term: Double) { ResultCard("نتيجة الاسترداد", "صافي ربح / خسارة الاسترداد", r.netGain) { ResultLine("الأيام المتبقية", (term - elapsed).toInt().toString() + " يوم"); ResultLine("تكلفة الشراء الأصلية", money(r.purchase.debit)); ResultLine("القيمة الحالية عند الاسترداد", money(r.proceeds)); ResultLine("الربح / الخسارة قبل الضريبة", money(r.gain)); ResultLine("ضريبة الاسترداد", money(r.tax)); ResultLine("المبلغ النهائي حسب معادلة Excel", money(r.finalAmount)) } }
@Composable private fun ResultCard(title: String, hero: String, value: Double, details: @Composable ColumnScope.() -> Unit) { Column(Modifier.padding(16.dp).fillMaxWidth().background(Navy, RoundedCornerShape(20.dp)).padding(18.dp)) { Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp); Text(hero, color = Color(0xFFB8C5D8), fontSize = 12.sp, modifier = Modifier.padding(top = 15.dp)); Text(money(value), color = Teal, fontSize = 25.sp, fontWeight = FontWeight.Bold); HorizontalDivider(color = Color(0xFF34435A), modifier = Modifier.padding(vertical = 12.dp)); details() } }
@Composable private fun ResultLine(label: String, value: String) { Row(Modifier.fillMaxWidth().padding(vertical = 5.dp), horizontalArrangement = Arrangement.SpaceBetween) { Text(label, color = Color(0xFFCFD8E5), fontSize = 11.sp, modifier = Modifier.weight(1f)); Text(value, color = Color.White, fontSize = 11.sp, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.weight(1f)) } }
@Composable private fun Disclaimer() { Row(Modifier.padding(16.dp).fillMaxWidth(), verticalAlignment = Alignment.Top) { Icon(Icons.Default.Info, null, tint = Muted, modifier = Modifier.size(17.dp)); Spacer(Modifier.width(7.dp)); Text("تنبيه: النتائج وفق معادلات ملف Excel المرفق وبافتراض ضريبة 20%. الحسابات تقديرية، ويجب التحقق من شروط البنك والضرائب المطبقة قبل تنفيذ الاستثمار.", color = Muted, fontSize = 10.sp) } }
@Composable private fun InfoCard(title: String, desc: String) { Card(Modifier.padding(16.dp).fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) { Column(Modifier.padding(18.dp)) { Text(title, color = Ink, fontWeight = FontWeight.Bold); Text(desc, color = Muted, fontSize = 12.sp, modifier = Modifier.padding(top = 6.dp)) } } }
