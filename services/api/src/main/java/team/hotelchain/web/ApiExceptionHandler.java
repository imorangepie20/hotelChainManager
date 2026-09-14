package team.hotelchain.web;

import team.hotelchain.reservation.BusinessConflictException;
import team.hotelchain.reservation.ReservationNotFoundException;
import team.hotelchain.operations.RoomHasActiveAssignmentsException;
import team.hotelchain.operations.RoomOperationsView;
import team.hotelchain.staff.StaffAccessDeniedException;
import team.hotelchain.staff.StaffAuthenticationException;
import team.hotelchain.webcontent.WebsitePageNotFoundException;
import team.hotelchain.webcontent.WebsiteMediaNotFoundException;
import team.hotelchain.webcontent.WebsiteTranslationReviewValidationException;
import team.hotelchain.webcontent.WebsitePreviewNotFoundException;
import team.hotelchain.webcontent.WebsitePreviewUnavailableException;

import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;

@RestControllerAdvice
public class ApiExceptionHandler {

    @ExceptionHandler(RoomHasActiveAssignmentsException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public RoomOperationalConflictError roomHasActiveAssignments(RoomHasActiveAssignmentsException exception) {
        return new RoomOperationalConflictError(exception.code(), exception.getMessage(), exception.assignments());
    }

    @ExceptionHandler(WebsitePreviewNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ResponseEntity<ApiError> websitePreviewNotFound(WebsitePreviewNotFoundException exception) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).cacheControl(CacheControl.noStore())
                .body(new ApiError(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(WebsitePreviewUnavailableException.class)
    @ResponseStatus(HttpStatus.GONE)
    public ResponseEntity<ApiError> websitePreviewUnavailable(WebsitePreviewUnavailableException exception) {
        return ResponseEntity.status(HttpStatus.GONE).cacheControl(CacheControl.noStore())
                .body(new ApiError(exception.code(), exception.getMessage()));
    }

    @ExceptionHandler(WebsiteTranslationReviewValidationException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError translationReviewValidation(WebsiteTranslationReviewValidationException exception) {
        return new ApiError(exception.code(), exception.getMessage());
    }

    @ExceptionHandler(IllegalArgumentException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError invalidRequest(IllegalArgumentException exception) {
        return new ApiError("INVALID_REQUEST", exception.getMessage());
    }

    @ExceptionHandler(BusinessConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError conflict(BusinessConflictException exception) {
        return new ApiError(exception.code(), exception.getMessage());
    }

    @ExceptionHandler(ReservationNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError notFound(ReservationNotFoundException exception) {
        return new ApiError("RESERVATION_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(WebsitePageNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError websitePageNotFound(WebsitePageNotFoundException exception) {
        return new ApiError("WEBSITE_PAGE_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(WebsiteMediaNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError websiteMediaNotFound(WebsiteMediaNotFoundException exception) {
        return new ApiError("WEBSITE_MEDIA_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(StaffAuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ApiError staffAuthentication(StaffAuthenticationException exception) {
        return new ApiError("STAFF_AUTHENTICATION_REQUIRED", exception.getMessage());
    }

    @ExceptionHandler(StaffAccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ApiError staffAccessDenied(StaffAccessDeniedException exception) {
        return new ApiError("STAFF_HOTEL_ACCESS_DENIED", exception.getMessage());
    }

    public record RoomOperationalConflictError(
            String code,
            String message,
            java.util.List<RoomOperationsView.ImpactedAssignment> assignments) {}
}
