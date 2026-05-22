package com.eaglenet.vpn

/**
 * Generates the set of CIDR routes that cover all of 0.0.0.0/0
 * **except** a single host IP.
 *
 * This is used to ensure that traffic destined for the upstream proxy
 * server itself does NOT go through the VPN TUN interface, which would
 * create an infinite routing loop.
 *
 * The algorithm does a binary tree walk: at each level it checks which
 * half of the address space contains the excluded IP and emits the
 * other half as a concrete CIDR, then recurses into the half that
 * contains the excluded IP until it reaches /32.
 */
object RouteUtils {

    data class Route(val address: String, val prefix: Int)

    /**
     * Returns a list of (address, prefixLength) pairs covering
     * 0.0.0.0/0 minus [excludeIp]/32.
     */
    fun routesExcluding(excludeIp: String): List<Route> {
        return try {
            val target = ipToLong(excludeIp)
            buildRoutes(0L, 0, target)
        } catch (e: Exception) {
            // If parsing fails just return the full default route.
            listOf(Route("0.0.0.0", 0))
        }
    }

    private fun buildRoutes(base: Long, prefix: Int, exclude: Long): List<Route> {
        if (prefix >= 32) return emptyList()

        val size = 1L shl (32 - prefix)   // number of addresses in this block
        val mid  = base + size / 2         // midpoint (start of upper half)

        return if (exclude < mid) {
            // Excluded IP is in the lower half → add upper half as a route
            // and recurse into lower half.
            buildRoutes(base, prefix + 1, exclude) +
                Route(longToIp(mid), prefix + 1)
        } else {
            // Excluded IP is in the upper half → add lower half as a route
            // and recurse into upper half.
            listOf(Route(longToIp(base), prefix + 1)) +
                buildRoutes(mid, prefix + 1, exclude)
        }
    }

    private fun ipToLong(ip: String): Long {
        val parts = ip.trim().split(".")
        require(parts.size == 4) { "Not a valid IPv4 address: $ip" }
        return parts.fold(0L) { acc, part -> (acc shl 8) or part.toLong() }
    }

    private fun longToIp(l: Long): String {
        return "${(l shr 24) and 0xFF}.${(l shr 16) and 0xFF}" +
               ".${(l shr 8) and 0xFF}.${l and 0xFF}"
    }
}
