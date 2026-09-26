package team.hotelchain.web;

import team.hotelchain.hotel.HotelNotFoundException;
import team.hotelchain.hotel.RateDayNotFoundException;
import team.hotelchain.hotel.RatePlanInventoryDayNotFoundException;
import team.hotelchain.hotel.RatePlanNameConflictException;
import team.hotelchain.hotel.RatePlanNotFoundException;
import team.hotelchain.hotel.RoomTypeBreakfastConflictException;
import team.hotelchain.hotel.RoomTypeDeletionConflictException;
import team.hotelchain.hotel.RoomTypeOccupancyConflictException;
import team.hotelchain.hotel.RoomTypeRatePlanNotFoundException;
import team.hotelchain.inventory.InventoryCapacityConflictException;
import team.hotelchain.inventory.InventoryDayNotFoundException;
import team.hotelchain.inventory.InventoryImportFormatException;
import team.hotelchain.inventory.SalesStatusConflictException;
import team.hotelchain.payment.settlement.SettlementNotRetryableException;
import team.hotelchain.payment.settlement.SettlementRunNotFoundException;
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
import team.hotelchain.guestrequest.GuestRequestNotFoundException;
import team.hotelchain.guestrequest.GuestRequestStateConflictException;

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

    @ExceptionHandler(HotelNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError hotelNotFound(HotelNotFoundException exception) {
        return new ApiError("HOTEL_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(RoomTypeOccupancyConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError roomTypeOccupancyConflict(RoomTypeOccupancyConflictException exception) {
        return new ApiError("ROOM_TYPE_OCCUPANCY_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(RoomTypeBreakfastConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError roomTypeBreakfastConflict(RoomTypeBreakfastConflictException exception) {
        return new ApiError("ROOM_TYPE_BREAKFAST_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(RoomTypeDeletionConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError roomTypeDeletionConflict(RoomTypeDeletionConflictException exception) {
        return new ApiError("ROOM_TYPE_DELETION_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(RoomTypeRatePlanNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError roomTypeRatePlanNotFound(RoomTypeRatePlanNotFoundException exception) {
        return new ApiError("ROOM_TYPE_RATE_PLAN_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(RateDayNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError rateDayNotFound(RateDayNotFoundException exception) {
        return new ApiError("RATE_DAY_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(RatePlanNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError ratePlanNotFound(RatePlanNotFoundException exception) {
        return new ApiError("RATE_PLAN_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(RatePlanNameConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError ratePlanNameConflict(RatePlanNameConflictException exception) {
        return new ApiError("RATE_PLAN_NAME_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(RatePlanInventoryDayNotFoundException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError ratePlanInventoryDayNotFound(RatePlanInventoryDayNotFoundException exception) {
        return new ApiError("RATE_PLAN_INVENTORY_DAY_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(SalesStatusConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError salesStatusConflict(SalesStatusConflictException exception) {
        return new ApiError("SALES_STATUS_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(InventoryImportFormatException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ApiError inventoryImportFormat(InventoryImportFormatException exception) {
        return new ApiError("INVENTORY_IMPORT_FORMAT", exception.getMessage());
    }

    @ExceptionHandler(InventoryCapacityConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError inventoryCapacityConflict(InventoryCapacityConflictException exception) {
        return new ApiError("INVENTORY_CAPACITY_CONFLICT", exception.getMessage());
    }

    @ExceptionHandler(InventoryDayNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError inventoryDayNotFound(InventoryDayNotFoundException exception) {
        return new ApiError("INVENTORY_DAY_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(SettlementRunNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError settlementRunNotFound(SettlementRunNotFoundException exception) {
        return new ApiError("SETTLEMENT_RUN_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(SettlementNotRetryableException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError settlementNotRetryable(SettlementNotRetryableException exception) {
        return new ApiError("SETTLEMENT_RUN_NOT_RETRYABLE", exception.getMessage());
    }

    @ExceptionHandler(GuestRequestNotFoundException.class)
    @ResponseStatus(HttpStatus.NOT_FOUND)
    public ApiError guestRequestNotFound(GuestRequestNotFoundException exception) {
        return new ApiError("GUEST_REQUEST_NOT_FOUND", exception.getMessage());
    }

    @ExceptionHandler(GuestRequestStateConflictException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ApiError guestRequestStateConflict(GuestRequestStateConflictException exception) {
        return new ApiError("GUEST_REQUEST_STATE_CONFLICT", exception.getMessage());
    }

    public record RoomOperationalConflictError(
            String code,
            String message,
            java.util.List<RoomOperationsView.ImpactedAssignment> assignments) {}
}
