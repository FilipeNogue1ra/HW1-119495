Feature: Customer parcel pickup from smart locker terminal
  As a TuttiBox customer
  I want to collect my parcel using a 6-digit PIN on the terminal
  So that I can securely retrieve my shipment 24/7

  Scenario: Customer successfully picks up a parcel with valid PIN
    Given a parcel with tracking code "TB-2026-PICKUP01" is ready for pickup at station "STATION-AVEIRO-01" in compartment "A01"
    And the customer has received the valid pickup PIN "842910"
    When the customer enters PIN "842910" at station "STATION-AVEIRO-01"
    Then the compartment door "A01" should unlock and open
    And when the customer closes compartment door "A01"
    Then the parcel state should be "PICKED_UP"
    And the compartment "A01" should be free for new shipments
