package com.eduadmin.school.service;

import org.springframework.stereotype.Service;

@Service
public class SmsServiceImpl implements SmsService {

    @Override
    public void send(String to, String message) {
        System.out.println("[SMS to " + to + "] " + message);
    }
}
