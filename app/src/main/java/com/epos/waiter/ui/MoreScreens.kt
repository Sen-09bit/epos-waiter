@file:OptIn(ExperimentalMaterial3Api::class)

package com.epos.waiter.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.epos.waiter.AppViewModel
import com.epos.waiter.Screen
import com.epos.waiter.data.money

@Composable
private fun BackBar(title: String, vm: AppViewModel) {
    TopAppBar(
        title = { Text(title) },
        navigationIcon = {
            IconButton(onClick = { vm.go(Screen.Tables) }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
        },
        colors = brandBar(), windowInsets = NoInsets,
    )
}

@Composable
fun ExpensesScreen(vm: AppViewModel) {
    var description by remember { mutableStateOf("") }
    var amount by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        BackBar("Expenses", vm)
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(value = description, onValueChange = { description = it }, label = { Text("Description") },
                singleLine = true, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(value = amount, onValueChange = { amount = it }, label = { Text("Amount") }, singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), modifier = Modifier.weight(1f))
                Button(onClick = { vm.addExpense(description, amount) { description = ""; amount = "" } },
                    enabled = !vm.busy, modifier = Modifier.height(56.dp)) { Text("Add expense") }
            }
        }
        HorizontalDivider()
        Text("Last 50 expenses", style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(16.dp, 12.dp))
        LazyColumn(Modifier.fillMaxSize()) {
            items(vm.expenses) { e ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp)) {
                    Column(Modifier.weight(1f)) {
                        Text(e.description?.trim().orEmpty(), fontWeight = FontWeight.SemiBold)
                        Text(listOfNotNull(e.date?.date?.take(10), e.paymentMode?.trim()).joinToString(" · "),
                            style = MaterialTheme.typography.labelMedium)
                    }
                    Text(money(e.amount ?: 0.0), fontWeight = FontWeight.Bold)
                }
                HorizontalDivider()
            }
        }
    }
}

@Composable
fun ProfileScreen(vm: AppViewModel) {
    val c = vm.concern
    // Re-create the form when the profile arrives from the server.
    var name by remember(c) { mutableStateOf(c?.concernName.orEmpty()) }
    var subTitle by remember(c) { mutableStateOf(c?.subTitle.orEmpty()) }
    var add1 by remember(c) { mutableStateOf(c?.add1.orEmpty()) }
    var add2 by remember(c) { mutableStateOf(c?.add2.orEmpty()) }
    var city by remember(c) { mutableStateOf(c?.city.orEmpty()) }
    var zip by remember(c) { mutableStateOf(c?.zipCode.orEmpty()) }
    var country by remember(c) { mutableStateOf(c?.country.orEmpty()) }
    var phone by remember(c) { mutableStateOf(c?.phone.orEmpty()) }
    var mobile by remember(c) { mutableStateOf(c?.mobileNo.orEmpty()) }
    var email by remember(c) { mutableStateOf(c?.email.orEmpty()) }
    var website by remember(c) { mutableStateOf(c?.website.orEmpty()) }
    var vat by remember(c) { mutableStateOf(c?.vatNo.orEmpty()) }

    Column(Modifier.fillMaxSize()) {
        BackBar("Restaurant profile", vm)
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 600.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Field("Name", name) { name = it }
                Field("Sub title", subTitle) { subTitle = it }
                Field("Address 1", add1) { add1 = it }
                Field("Address 2", add2) { add2 = it }
                Field("City", city) { city = it }
                Field("Zip code", zip) { zip = it }
                Field("Country", country) { country = it }
                Field("Phone", phone) { phone = it }
                Field("Mobile", mobile) { mobile = it }
                Field("E-mail", email) { email = it }
                Field("Website", website) { website = it }
                Field("Tax no. (VAT / GST)", vat) { vat = it }
                Button(
                    enabled = c != null && !vm.busy,
                    onClick = {
                        c?.let {
                            vm.saveProfile(it.copy(concernName = name, subTitle = subTitle, add1 = add1, add2 = add2, city = city,
                                zipCode = zip, country = country, phone = phone, mobileNo = mobile, email = email,
                                website = website, vatNo = vat))
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(52.dp),
                ) { Text("Save") }
            }
        }
    }
}

@Composable
private fun Field(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        modifier = Modifier.fillMaxWidth())
}

@Composable
fun PasswordScreen(vm: AppViewModel) {
    var old by remember { mutableStateOf("") }
    var new by remember { mutableStateOf("") }
    var confirm by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize()) {
        BackBar("Change password", vm)
        Column(Modifier.fillMaxSize().padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Column(Modifier.widthIn(max = 520.dp).fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(vm.user?.name.orEmpty(), style = MaterialTheme.typography.titleMedium)
                PasswordField("Old password", old) { old = it }
                PasswordField("New password", new) { new = it }
                PasswordField("Confirm new password", confirm) { confirm = it }
                Button(onClick = { vm.changePassword(old, new, confirm) }, enabled = !vm.busy,
                    modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Change password") }
            }
        }
    }
}

@Composable
private fun PasswordField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value = value, onValueChange = onChange, label = { Text(label) }, singleLine = true,
        visualTransformation = PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
        modifier = Modifier.fillMaxWidth())
}
