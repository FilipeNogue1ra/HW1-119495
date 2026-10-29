Feature: Hardware failure handling during pickup
  As a TuttiBox operator
  I want mechanical door jamming during pickup to trigger an operator alert
  So that customer parcels are safe and maintenance is dispatched immediately

  Scenario: Locker compartment door jams during customer unlock
    Given a parcel with tracking code "TB-2026-JAM001" is ready for pickup at station "STATION-AVEIRO-01" in compartment "A01"
    And the locker hardware has a simulated solenoid jam on compartment "A01"
    When the customer enters PIN "842910" for parcel "TB-2026-JAM001" at station "STATION-AVEIRO-01"
    Then the system should detect the jammed door
    And the parcel should transition to state "WAITING_FOR_OPERATOR"
    And compartment "A01" should be placed "OUT_OF_SERVICE"
