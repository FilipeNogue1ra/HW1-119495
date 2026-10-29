package pt.ua.deti.tqs.tuttibox.infrastructure.adapters.in.web.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import pt.ua.deti.tqs.tuttibox.core.model.Dimension;

public class CreateParcelRequest {

    @NotNull(message = "Parcel dimension is required (S, M, or L)")
    private Dimension dimension;

    @NotBlank(message = "Recipient phone is required")
    @Pattern(regexp = "^\\+?[0-9]{9,15}$", message = "Invalid phone number format")
    private String recipientPhone;

    @NotBlank(message = "Recipient email is required")
    @Email(message = "Invalid email address format")
    private String recipientEmail;

    @NotBlank(message = "Target station code is required")
    private String stationCode;

    public CreateParcelRequest() {
    }

    public CreateParcelRequest(Dimension dimension, String recipientPhone, String recipientEmail, String stationCode) {
        this.dimension = dimension;
        this.recipientPhone = recipientPhone;
        this.recipientEmail = recipientEmail;
        this.stationCode = stationCode;
    }

    public Dimension getDimension() {
        return dimension;
    }

    public void setDimension(Dimension dimension) {
        this.dimension = dimension;
    }

    public String getRecipientPhone() {
        return recipientPhone;
    }

    public void setRecipientPhone(String recipientPhone) {
        this.recipientPhone = recipientPhone;
    }

    public String getRecipientEmail() {
        return recipientEmail;
    }

    public void setRecipientEmail(String recipientEmail) {
        this.recipientEmail = recipientEmail;
    }

    public String getStationCode() {
        return stationCode;
    }

    public void setStationCode(String stationCode) {
        this.stationCode = stationCode;
    }
}
