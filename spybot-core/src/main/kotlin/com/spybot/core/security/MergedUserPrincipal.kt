package com.spybot.core.security

import com.spybot.core.model.MergedUserView
import org.springframework.security.core.GrantedAuthority
import org.springframework.security.core.authority.SimpleGrantedAuthority
import org.springframework.security.core.userdetails.UserDetails

data class MergedUserPrincipal(
    val user: MergedUserView,
) : UserDetails {
    override fun getAuthorities(): Collection<GrantedAuthority> =
        buildList {
            add(SimpleGrantedAuthority("ROLE_USER"))
            if (user.isSuperuser) {
                add(SimpleGrantedAuthority("ROLE_ADMIN"))
            }
        }

    override fun getPassword(): String = ""

    override fun getUsername(): String = user.id.toString()

    override fun isAccountNonExpired(): Boolean = true

    override fun isAccountNonLocked(): Boolean = true

    override fun isCredentialsNonExpired(): Boolean = true

    override fun isEnabled(): Boolean = !user.obsolete

    val id: Long
        get() = user.id

    val displayName: String
        get() = user.name
}
