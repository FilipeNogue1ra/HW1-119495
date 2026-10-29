Feature: Terminal security lockout after failed PIN attempts
  As a TuttiBox operator
  I want the locker terminal to lock after 3 consecutive wrong PIN attempts
  So that brute-force attacks on 6-digit PINs are prevented

  Scenario: Customer enters 3 consecutive incorrect PINs triggering 60-second lockout
    Given the terminal at station "STATION-AVEIRO-01" is operational with 0 failed attempts
    When a user enters an incorrect PIN "000000" at station "STATION-AVEIRO-01"
    Then the terminal should report 1 failed attempt and remain unlocked
    When a user enters an incorrect PIN "111111" at station "STATION-AVEIRO-01"
    Then the terminal should report 2 failed attempts and remain unlocked
    When a user enters an incorrect PIN "222222" at station "STATION-AVEIRO-01"
    Then the terminal should become locked for 60 seconds
    And any subsequent PIN entry during lockout should be rejected with a lockout error
