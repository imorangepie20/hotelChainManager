package team.hotelchain.webcontent;

import team.hotelchain.reservation.BusinessConflictException;

public class WebsiteMediaConflictException extends BusinessConflictException {
    public WebsiteMediaConflictException(String code, String message) {
        super(code, message);
    }
}
