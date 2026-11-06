console-banking-ledger/
├── pom.xml
├── README.md
├── .gitignore
├── data/                      (created at runtime, git-ignored)
└── src/
    ├── main/java/com/team/banking/
    │   ├── Main.java
    │   ├── model/             
    │   ├── strategy/           
    │   ├── factory/           
    │   ├── exception/         
    │   ├── service/            
    │   ├── repository/         (interfaces)
    │   │   └── json/           (implementations)
    │   ├── command/           
    │   └── cli/               
    └── test/java/com/team/banking/
        ├── model/
        ├── service/
        └── repository/