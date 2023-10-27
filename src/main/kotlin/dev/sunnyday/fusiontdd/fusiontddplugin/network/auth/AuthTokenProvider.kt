package dev.sunnyday.fusiontdd.fusiontddplugin.network.auth

fun interface AuthTokenProvider {

    fun getAuthToken(): String
}