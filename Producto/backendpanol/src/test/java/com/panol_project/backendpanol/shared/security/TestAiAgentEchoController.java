package com.panol_project.backendpanol.shared.security;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v2/test-ai-agent")
class TestAiAgentEchoController {

    @GetMapping
    String get() {
        return "ok";
    }

    @PostMapping
    String post() {
        return "created";
    }

    @PutMapping
    String put() {
        return "updated";
    }

    @PatchMapping
    String patch() {
        return "patched";
    }

    @DeleteMapping
    String delete() {
        return "deleted";
    }
}
