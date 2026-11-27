package com.example.helpdesk.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/** Enables the SLA escalation sweep; multi-instance deployments add ShedLock in a later phase. */
@Configuration
@EnableScheduling
public class SchedulingConfig {}
