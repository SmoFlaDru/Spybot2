package com.spybot.web.controller

import com.spybot.core.security.MergedUserPrincipal
import com.spybot.core.service.SpybotQueryService
import com.spybot.core.service.SteamService
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Pattern
import org.springframework.http.HttpHeaders
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.security.core.annotation.AuthenticationPrincipal
import org.springframework.validation.annotation.Validated
import org.springframework.web.bind.annotation.DeleteMapping
import org.springframework.web.bind.annotation.PathVariable
import org.springframework.web.bind.annotation.PostMapping
import org.springframework.web.bind.annotation.RequestMapping
import org.springframework.web.bind.annotation.RequestParam
import org.springframework.web.bind.annotation.RestController

@RestController
@Validated
@RequestMapping("/profile")
class ProfileController(
    private val queryService: SpybotQueryService,
    private val steamService: SteamService,
) {
    @DeleteMapping("/passkey/{id}")
    fun deletePasskey(
        @AuthenticationPrincipal principal: MergedUserPrincipal,
        @PathVariable id: Long,
    ): ResponseEntity<Void> =
        if (queryService.deletePasskey(principal.id, id)) {
            ResponseEntity.noContent().build()
        } else {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }

    @PostMapping("/steamid")
    fun addSteamId(
        @AuthenticationPrincipal principal: MergedUserPrincipal,
        @RequestParam("steamid") @Pattern(regexp = "\\d{5,20}") steamId: String,
        @RequestParam("name") @NotBlank accountName: String,
    ): ResponseEntity<String> {
        if (steamService.getSteamUsersPlayingInfo(listOf(steamId)).isEmpty()) {
            return ResponseEntity
                .badRequest()
                .body("Could not verify this Steam ID. Please double check that it's correct.")
        }
        queryService.addSteamId(principal.id, steamId.toLong(), accountName)
        return ResponseEntity
            .noContent()
            .header("HX-Trigger", "steamids_changed")
            .build()
    }

    @DeleteMapping("/steamid/{id}")
    fun deleteSteamId(
        @AuthenticationPrincipal principal: MergedUserPrincipal,
        @PathVariable id: Long,
    ): ResponseEntity<Void> =
        if (queryService.deleteSteamId(principal.id, id)) {
            ResponseEntity
                .status(HttpStatus.OK)
                .header("HX-Trigger", "steamids_changed")
                .build()
        } else {
            ResponseEntity.status(HttpStatus.FORBIDDEN).build()
        }
}
