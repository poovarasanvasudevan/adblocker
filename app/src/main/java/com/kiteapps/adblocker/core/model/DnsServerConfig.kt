package com.kiteapps.adblocker.core.model

data class DnsServerConfig(
    val id: String,
    val name: String,
    val description: String,
    val protocol: String = "DOH", // "DOH", "UDP", "DOT"
    val primary: String,
    val fallback: String,
    val dohUrl: String,
    val isCustom: Boolean = false
) {
    companion object {
        val CLOUDFLARE = DnsServerConfig(
            id = "cloudflare",
            name = "Cloudflare DNS",
            description = "Fast, private 1.1.1.1 DNS over HTTPS",
            protocol = "DOH",
            primary = "1.1.1.1",
            fallback = "1.0.0.1",
            dohUrl = "https://cloudflare-dns.com/dns-query"
        )

        val GOOGLE = DnsServerConfig(
            id = "google",
            name = "Google Public DNS",
            description = "Reliable 8.8.8.8 DNS over HTTPS",
            protocol = "DOH",
            primary = "8.8.8.8",
            fallback = "8.8.4.4",
            dohUrl = "https://dns.google/dns-query"
        )

        val ADGUARD = DnsServerConfig(
            id = "adguard",
            name = "AdGuard Default DNS",
            description = "Blocks ads and trackers at resolver level",
            protocol = "DOH",
            primary = "94.140.14.14",
            fallback = "94.140.15.15",
            dohUrl = "https://dns.adguard-dns.com/dns-query"
        )

        val QUAD9 = DnsServerConfig(
            id = "quad9",
            name = "Quad9 Security DNS",
            description = "Blocks malware, spyware, and phishing",
            protocol = "DOH",
            primary = "9.9.9.9",
            fallback = "149.112.112.112",
            dohUrl = "https://dns.quad9.net/dns-query"
        )

        val PRESETS = listOf(CLOUDFLARE, GOOGLE, ADGUARD, QUAD9)
    }
}
