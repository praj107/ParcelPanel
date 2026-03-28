package com.parcelpanel.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import java.io.File
import java.security.MessageDigest

data class InstallLaunchResult(
    val installerOpened: Boolean,
    val message: String,
)

object ApkInstaller {
    fun launchInstall(context: Context, apkFile: File): InstallLaunchResult {
        if (!apkFile.exists()) {
            return InstallLaunchResult(
                installerOpened = false,
                message = "The downloaded update package is no longer available.",
            )
        }
        if (!context.packageManager.canRequestPackageInstalls()) {
            val permissionIntent = Intent(
                Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES,
                Uri.parse("package:${context.packageName}")
            ).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(permissionIntent)
            return InstallLaunchResult(
                installerOpened = false,
                message = "Allow ParcelPanel to install updates, then tap install again.",
            )
        }

        val contentUri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            apkFile,
        )
        val installIntent = Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(contentUri, "application/vnd.android.package-archive")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(installIntent)
        return InstallLaunchResult(
            installerOpened = true,
            message = "Android package installer opened.",
        )
    }
}

object ApkSignatureVerifier {
    fun matchesInstalledSigning(context: Context, apkFile: File): Boolean {
        val packageManager = context.packageManager
        val archivePackageName = archivePackageName(packageManager, apkFile)
        if (archivePackageName != context.packageName) {
            return false
        }
        val installedDigests = installedSigningDigests(packageManager, context.packageName)
        val archiveDigests = archiveSigningDigests(packageManager, apkFile)
        return installedDigests.isNotEmpty() && installedDigests == archiveDigests
    }

    @Suppress("DEPRECATION")
    private fun installedSigningDigests(
        packageManager: PackageManager,
        packageName: String,
    ): Set<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageInfo(
                    packageName,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
                )
            } else {
                packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNING_CERTIFICATES)
            }
            val signatures = info.signingInfo?.let { signingInfo ->
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            }.orEmpty()
            signatures.map { signature -> signature.toByteArray().sha256Hex() }.toSet()
        } else {
            val info = packageManager.getPackageInfo(packageName, PackageManager.GET_SIGNATURES)
            info.signatures.orEmpty().map { signature -> signature.toByteArray().sha256Hex() }.toSet()
        }
    }

    @Suppress("DEPRECATION")
    private fun archiveSigningDigests(
        packageManager: PackageManager,
        apkFile: File,
    ): Set<String> {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            val info = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                packageManager.getPackageArchiveInfo(
                    apkFile.absolutePath,
                    PackageManager.PackageInfoFlags.of(PackageManager.GET_SIGNING_CERTIFICATES.toLong())
                )
            } else {
                packageManager.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
            } ?: return emptySet()
            val signatures = info.signingInfo?.let { signingInfo ->
                if (signingInfo.hasMultipleSigners()) {
                    signingInfo.apkContentsSigners
                } else {
                    signingInfo.signingCertificateHistory
                }
            }.orEmpty()
            signatures.map { signature -> signature.toByteArray().sha256Hex() }.toSet()
        } else {
            val info = packageManager.getPackageArchiveInfo(apkFile.absolutePath, PackageManager.GET_SIGNATURES)
                ?: return emptySet()
            info.signatures.orEmpty().map { signature -> signature.toByteArray().sha256Hex() }.toSet()
        }
    }

    @Suppress("DEPRECATION")
    private fun archivePackageName(
        packageManager: PackageManager,
        apkFile: File,
    ): String? {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            packageManager.getPackageArchiveInfo(
                apkFile.absolutePath,
                PackageManager.PackageInfoFlags.of(0)
            )?.packageName
        } else {
            packageManager.getPackageArchiveInfo(apkFile.absolutePath, 0)?.packageName
        }
    }

    private fun ByteArray.sha256Hex(): String =
        MessageDigest.getInstance("SHA-256")
            .digest(this)
            .joinToString(separator = "") { byte -> "%02x".format(byte) }
}
