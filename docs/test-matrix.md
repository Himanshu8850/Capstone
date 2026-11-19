# Test matrix: requirement → automated test or manual check

Source of the requirement IDs: `specs/001-bookygeek-platform/spec.md` §2 (FR) and §3 (NFR). Task T134 (NFR-TST-01: each
business rule has an explicit test, and the list maps rule → test).

## How to read this document

- **Backend unit** = `backend/src/test/java/com/bookygeek/unit/**` (JUnit 5 + Mockito, run by Surefire).
- **Backend IT** = `integration/*IT.java` (Spring Boot + PostgreSQL, run by Failsafe).
- **Contract** = `contract/*ContractTest.java` (response validated against `api/openapi.yaml`, run by Failsafe). The
  operation-to-test mapping is in [`api/TRACEABILITY.md`](../api/TRACEABILITY.md).
- **Architecture** = `architecture/ModuleBoundaryTest` (module boundaries, naming, no repository or entity shared
  across modules).
- **FE** = Vitest + React Testing Library under `frontend/src/**/__tests__/` (and `frontend/src/test/`).
- **Banned strings** = `frontend/src/test/bannedStrings.test.jsx` (excluded features are never shown).
- **E2E-nn** = Playwright, `e2e/tests/e2e-0n-*.spec.js` (Chromium only so far; see "Not yet verified").
- Test names are the Java method or the Vitest `it(...)` title, shortened. A class name alone means the whole class.
- **Manual / pending** marks a requirement that no automated test can prove. The task that records the evidence is named.
  Nothing is marked as verified unless it was run.

## 1. Functional requirements

### Authentication (FR-AUTH)

| ID | Automated test | Manual check |
|---|---|---|
| FR-AUTH-01 | `AuthFlowIT.registerCreatesTheCustomerAndTheRewardAccountWithOneThousandPoints`, `…emailIsStoredLowerCasedAndAnyLetterCaseOfItIsADuplicate`; `AuthServiceTest.emailThatExistsInAnyLetterCaseIsRejectedWithEmailTaken`; `AuthContractTest`; FE `RegisterPage`; E2E-01 (duplicate e-mail in another letter case) | — |
| FR-AUTH-02 | `AuthServiceTest.passwordMustBeEightToSixtyFourCharactersWithALetterAndADigit`, `…mismatchingConfirmationIsAValidationErrorOnConfirmPassword`; `AuthFlowIT.invalidRegistrationListsEveryInvalidFieldAndCreatesNothing`; FE `validators.test` "registration rules (FR-AUTH-02)", `RegisterPage` | — |
| FR-AUTH-03 | `AuthFlowIT.loginSetsAHttpOnlySameSiteStrictCookieScopedToApi`; `AuthServiceTest.loginIsCaseInsensitiveOnEmailAndIssuesATokenForThePresentVersion`; `SecurityComponentsTest` (token issue, expiry, tamper, cookie flags); `SecurityBaselineIT.tamperedExpiredStaleAndUnknownCustomerTokensAreUnauthenticated`; FE `LoginPage`, `AuthContext`; E2E-01 | — |
| FR-AUTH-04 | `AuthFlowIT.wrongPasswordAndUnknownEmailGiveTheSameGenericError`; `AuthServiceTest.unknownEmailRunsTheDummyHashComparisonAndIsIndistinguishableFromAWrongPassword` | — |
| FR-AUTH-05 | `AuthFlowIT.logoutExpiresBothCookiesBumpsTheTokenVersionAndRejectsAReplayedCookie`; `AuthServiceTest.logoutBumpsTheTokenVersion`; FE `AccountMenu`, `layout.test` Header (working Log out); E2E-01 | — |
| FR-AUTH-06 | `SecurityBaselineIT.protectedReadWithoutCookieIsUnauthenticatedApiError`, `…unknownPathsAreAlsoProtectedByDefault`; `BasketIT.everyOperationExceptPreviewNeedsALogin`; `OrderHistoryIT.listingOrdersNeedsALogin`; FE `routes.test` "protected routes", `returnTo.test`; E2E-01 (return to the requested page), E2E-04 (checkout asks a guest to log in) | — |
| FR-AUTH-07 | `BasketIT.eachCustomerSeesOnlyTheirOwnBasket`; `AddressIT.updatingReplacesTheAddressAndOnlyTheOwnerMayDoIt`, `…deletingRemovesTheAddressAndOnlyTheOwnerMayDoIt`; `PlaceOrderIT.anOrderCanOnlyBeReadByItsOwner`; `OrderHistoryIT.aCustomerOnlySeesTheirOwnOrders`; `RewardRedemptionIT.getRewardAccountReturnsOnlyTheCallersAccount`; `CancelOrderIT.anotherCustomersOrderAndAnUnknownOrderAreNotFound`; E2E-07 (foreign order not found). Full sweep: `SecurityRegressionIT.aCustomerCanNeverReadOrChangeAnotherCustomersData`, `SecurityRegressionIT.anotherCustomersSavedAddressCannotBeUsedToPlaceAnOrder`, `SecurityRegressionIT.theRewardAccountIsOnlyEverTheCallersOwn` | — |
| FR-AUTH-08 | `CatalogueQueryIT.theCatalogueIsPublicAndNeedsNoLogin`; `SecurityBaselineIT.publicOperationsNeedNeitherCookieNorCsrfToken`; `BasketIT.previewIsPublicPricesTheLinesAndWritesNothing`, `…mergeSumsCapsSkipsAndReportsEveryAdjustment`; FE `BasketContext.merge.test`, `contexts.test` "BasketContext as a guest"; E2E-02, E2E-03, E2E-04 | — |

### Landing page and catalogue (FR-LAND, FR-CAT)

| ID | Automated test | Manual check |
|---|---|---|
| FR-LAND-01 | FE `CataloguePage` "shows the catalogue, the category tree with All, and the Brands list to a guest"; E2E-02 | Wireframe comparison SCR-01/04 (T139) |
| FR-LAND-02 | FE `layout.test` Header (guest: brand, My Orders, basket, Log in; customer: name and Log out; menu icon only with a sidebar), `Sidebar.test`; Banned strings (no inert or excluded element) | Keyboard walk (T139) |
| FR-LAND-03 | FE `RecommendedForYou.test`; `OrderHistoryIT.aCustomerWithoutOrderHistoryGetsNoRecommendations`, `…recommendationsComeFromTheOrderedBooksCategories…`; E2E-07 (appears only with history); Banned strings (no Bestsellers, no New Launches) | — |
| FR-LAND-04 | FE `CataloguePage` "shows the plain empty state when the catalogue itself is empty", "shows a loading indicator, then an error with Retry" | — |
| FR-CAT-01 | `CatalogueQueryIT.aParentCategoryReturnsTheBooksOfAllItsDescendantLeaves`, `…aLeafCategoryReturnsOnlyItsOwnBooks…`, `…categoriesAreATwoLevelTree`; `CategoryServiceTest`; FE `Sidebar.test`, `CataloguePage` "selecting a category…", "toggles the category sidebar from the header menu icon"; E2E-02 | — |
| FR-CAT-02 | `CatalogueQueryIT.brandListsOnlyThatPublishersBooks`, `…brandsAreThePublishersWithTheirCountsInAlphabeticalOrder`; FE `CataloguePage` "selecting a brand filters by it and All brands clears it"; E2E-02 | — |
| FR-CAT-03 | `CatalogueQueryIT.authorFiltersByExactAuthorName`, `…aBookSummaryCarriesAvailabilityStockAndTheDeliveryDateRule`; FE `BookCard.test` (all fields; author and genre filter instead of a profile page); E2E-03 (author link filters the catalogue) | — |
| FR-CAT-04 | `CatalogueQueryIT.searchMatchesAuthorIgnoringCaseAndWildcardsAreLiteral`, `…sqlInjectionTextInASearchIsTreatedAsPlainText`; `RelevanceSortTest`; FE `CataloguePage` "searches by text and keeps it in the URL"; E2E-02 | — |
| FR-CAT-05 | `CatalogueQueryIT.languageAndFormatFilter`, `…combinedFiltersAreAnded`, `…filtersCombineWithSearchBrandSortAndPaging`, `…priceBandsFromConfigurationAreInclusiveAtBothEnds`, `…filterOptionsOnlyListLanguagesThatExistInListedBooks`; `BookygeekPropertiesTest.priceBandsKeepOpenEndedBounds`; FE `CataloguePage` "applies Language, Format and Price filters together and each defaults to All"; E2E-02 | — |
| FR-CAT-06 | `CatalogueQueryIT.priceSortsAreAscendingAndDescending`, `…titleSortIsAlphabeticalIgnoringCase`, `…relevanceRanksTitleStartsWithBeforeTitleContains`; `RelevanceSortTest`; FE `CataloguePage` "offers the four sort options with Relevance as the default"; E2E-02 | — |
| FR-CAT-07 | FE `BookCard.test` "opens the book page from the title"; E2E-02 ("the book card opens the book page address") | — |
| FR-CAT-08 | `CatalogueQueryIT.defaultOrderWithoutASearchIsAvailableFirstThenNewestAndAnOutOfStockBookIsLast`; `BookDetailIT.aPhysicalBookWithNoStockIsUnavailableButKeepsTheFiveDayDeliveryDate`; `CartServiceTest.aPhysicalBookNeverExceedsItsStock`; FE `BookCard.test` "disables Add to Basket for a physical book with no stock", `BookDetailPage` "marks a physical book with no stock as Unavailable"; E2E-03, E2E-04 | — |
| FR-CAT-09 | Same tests as FR-LAND-03 | — |
| FR-CAT-10 | `CatalogueQueryIT.pagingIsExactlyTwelveAndTheLastPageHoldsTheRest`, `…pagesDoNotOverlap`, `…aPageBeyondTheEndIsEmptyButKeepsTheTotals`; FE `CataloguePage` "pages with Previous and Next, keeps the filters, and returns to page 1 when a filter changes"; E2E-02 | — |

### Book detail (FR-DET)

| ID | Automated test | Manual check |
|---|---|---|
| FR-DET-01 | `BookDetailIT.aGuestCanReadAPhysicalBookWithItsDetails`; `BookDetailServiceTest.theDetailCarriesTheBackCoverTwoDecimalPriceAndBookCategories`; FE `BookDetailPage` "shows the details of a physical book…"; E2E-03 | Wireframe comparison SCR-05 (T139) |
| FR-DET-02 | `BookDetailServiceTest.aPhysicalBookIsDeliveredFiveDaysFromToday`, `…anEbookIsDeliveredToday`, `…theFiveDaysCrossAMonthAndYearEnd`, `…stockDoesNotChangeTheDeliveryDateAndZeroStockIsUnavailable` (fixed clock); `BookDetailIT.anEbookHasNoStockCountIsAlwaysAvailableAndIsDeliveredToday`; FE `utils.test` `formatDeliveryDate`; E2E-03 | — |
| FR-DET-03 | FE `BookDetailPage` "adds the book to the basket (FR-BSK-01)…", `BookCard.test` "adds the book to the basket from the card"; E2E-04 | — |
| FR-DET-04 | `BookDetailIT.relatedBooksShareACategoryWithTheBookAndExcludeTheBookItself`, `…aBookInTwoCategoriesGetsTheBooksOfBothWithoutDuplicates`, `…booksInAnotherCategoryAreNotRelated`, `…aBookWithNoOtherBookInItsCategoriesHasAnEmptyList`; FE `BookDetailPage` "shows Related Reads as compact cards…", "…empty state in Related Reads"; E2E-03 | — |
| FR-DET-05 | FE `BookDetailPage` "…opens a related book from its title"; E2E-03 | — |
| FR-DET-06 | `BookDetailIT.theBreadcrumbFollowsTheCategoryHierarchy`; `BookDetailServiceTest.theBreadcrumbIsThePathToTheFirstCategoryOfTheBook`; FE `BookDetailPage` "shows a breadcrumb that follows the category hierarchy…"; E2E-03 | — |
| FR-DET-07 | Amended by AMD-002: FE `BookDetailPage` "About the writer" section and average rating tests; `ReviewIT`; Banned strings | — |
| FR-DET-08 | Amended by AMD-002 (reviews are built; wishlist and My Writers are not): `ReviewIT` (list, create, HALF_UP average, 409 `ALREADY_REVIEWED`, 400 for rating 0 and 6, 401, 404); FE `BookDetailPage` reviews tests; `BookCard.test` "has no wishlist control", `BasketPage` "shows no wishlist, coupon or wallet control"; Banned strings; E2E-09 | — |
| AMD-002 (T156) | FE `QuantityControl.test` (add, plus, minus to removal, plus disabled at the limit, Go to Cart link); E2E-09 | — |
| AMD-002 (T157) | FE `ProfilePage.test`, `routes.test`; E2E-09 (open `/profile`) | — |
| AMD-002 (T160) | FE `common.test` `Field` (`required` renders ` *` and the native attribute), form tests | — |
| FR-DET-09 | `BookDetailIT.anUnknownBookIsBookNotFound`, `…anIdThatIsNotANumberOrIsOutOfRangeIsNotFoundWithTheStandardErrorBody`, `…theErrorBodyNeverRevealsInternals`; FE `BookDetailPage` "shows a not-found state with a way back…"; E2E-03 | — |

### Basket (FR-BSK)

| ID | Automated test | Manual check |
|---|---|---|
| FR-BSK-01 | `CartServiceTest.addingAnExistingBookIncreasesItsQuantity`, `…anEleventhUnitIsRejectedWithQuantityLimitAndNothingChanges`, `…aPhysicalBookNeverExceedsItsStock`, `…anEBookHasNoStockLimitOnlyTheTenUnitLimit`; `BasketIT.addingTheSameBookTwiceKeepsOneRowPerCustomerAndBook`, `…anEleventhUnitIsAConflictAndTheStoredQuantityIsUnchanged`, `…aPhysicalBookCannotExceedItsStockButAnEBookCan`, `…addingDoesNotReserveStock`, `…theDatabaseRejectsAQuantityOutsideOneToTen`; `BasketContractTest`; FE `BasketContext.merge.test` "guest limits checked in the browser", `BookCard.test`; E2E-04 (11th unit rejected, guest basket survives a reload) | — |
| FR-BSK-02 | FE `BasketPage` "lists each line with cover, title, author, format, unit price, delivery date, quantity and line total" | Wireframe comparison SCR-06 (T139) |
| FR-BSK-03 | `CartServiceTest.updatingSetsTheQuantityAndChecksTheSameLimits`; `BasketIT.updateAndRemoveChangeOnlyTheCallersOwnLine`; FE `BasketPage` "changes the quantity with the stepper", "cannot go below 1 … or above 10", "disables Increase at the stock of a physical book"; E2E-04 | — |
| FR-BSK-04 | `CartServiceTest.updatingOrRemovingABookNotInTheBasketIsNotFound`; `BasketIT.aCustomerCannotChangeOrRemoveALineTheyDoNotHave`; FE `BasketPage` "removes a line", "shows an empty state with a way back when the basket is empty"; E2E-04 | — |
| FR-BSK-05 | `PricingServiceTest` (all nine tests: tax 5% once per order, HALF_UP at the boundary, delivery ₹40 once per order, ₹0 for eBook-only, scale 2, empty order); `BasketIT.previewIsPublicPricesTheLinesAndWritesNothing`; FE `BasketPage` "shows the totals breakdown exactly as the API returned it", "shows free delivery for an eBook-only basket", `utils.test` `formatMoney`; Banned strings (no coupon text) | — |
| FR-BSK-06 | FE `layout.test` Header "shows the item count badge only when the basket is not empty", "uses the singular for one item"; `useBasket.test`; E2E-04 | — |
| FR-BSK-07 | `RecommendationServiceTest`; `OrderHistoryIT.recommendationsComeFromTheOrderedBooksCategoriesWithoutBasketBooksOrUnavailableOnes`, `…aCustomerWithoutOrderHistoryGetsNoRecommendations`, `…recommendationsNeedALogin`; `OrderContractTest` (`getBasketRecommendations`); FE `RecommendedForYou.test`; E2E-07 | — |
| FR-BSK-08 | `PlaceOrderIT.theQuoteReportsABasketLineThatNoLongerHasStock`, `…aWrongExpectedTotalIsABasketChangedConflictAndNothingChanges`, `…aPriceChangeBetweenQuoteAndPlacementIsABasketChangedConflict`, `…stockThatDroppedBelowTheBasketQuantityIsOutOfStockAndNothingChanges`; `OrderServiceTest.aPriceChangedUnderTheLockIsBasketChanged`, `…stockBelowTheBasketQuantityUnderTheLockIsOutOfStock`, `…aBookThatLeftTheCatalogueIsBasketChanged`; FE `CheckoutPage` "disables Pay Now and lists a basket problem reported by the quote", `PaymentPage` "shows the server message for a changed basket and refreshes the basket" | — |
| FR-BSK-09 | `BasketIT.addingTheSameBookTwiceKeepsOneRowPerCustomerAndBook` (rows are per customer and book in the database); `BasketIT.mergeSumsCapsSkipsAndReportsEveryAdjustment`; `AuthFlowIT.aNewLoginAfterLogoutWorksAgainWithTheNewVersion`; FE `contexts.test` "BasketContext as a customer"; E2E-04 (basket merged at login) | Log out and in again with a non-empty basket (T141) |

### Orders (FR-ORD)

| ID | Automated test | Manual check |
|---|---|---|
| FR-ORD-01 | `OrderHistoryIT.ordersAreListedNewestFirstWithTheirItemsAndPayableTotal`, `…aCustomerWithoutOrdersGetsAnEmptyList`, `…aCustomerOnlySeesTheirOwnOrders`, `…aCancelledOrderStaysInTheListWithItsStatus`; `OrderContractTest`; FE `OrdersPage`; E2E-07 | — |
| FR-ORD-02 | `PlaceOrderIT.theOrderKeepsASnapshotOfTheAddressAndThePrices`; `OrderHistoryIT.anOrderKeepsItsPricesWhenTheCataloguePriceAndStockChangeLater`; FE `OrderDetailPage`; E2E-07 | — |
| FR-ORD-03 | `BuyAgainServiceTest` (all seven); `OrderHistoryIT.buyAgainPutsEveryBookBackInTheBasketAtTheCurrentPriceAndLeavesTheOrderAlone`, `…buyAgainReportsABookThatIsOutOfStockAndDoesNotAddIt`, `…buyAgainReducesAQuantityAboveTheStockAndReportsIt`, `…buyAgainReportsABookThatIsNoLongerListedWithItsRealTitle`, `…buyAgainAddsToWhatIsAlreadyInTheBasketUpToTheTenUnitLimit`; FE `OrdersPage` "adds the books with Buy Again…"; E2E-07 | — |
| FR-ORD-04 | **Boundary:** `CancellationRulesTest.anOrderThatIsFortySevenHoursFiftyNineMinutesFiftyNineSecondsOldCanBeCancelled`, `…anOrderExactlyFortyEightHoursOldIsRejectedAndNothingChanges`, `…anOrderOlderThanFortyEightHoursIsRejected`, `…theWindowIsTheSameForACashOnDeliveryOrder` (fixed clock); `OrderServiceTest.anOrderExactlyAtTheWindowIsNotCancellable`; `CancelOrderIT.anOrderJustInsideTheFortyEightHourWindowCanStillBeCancelled`, `…anOrderPastTheWindowIsRejectedAndNothingChanges`, `…theWindowAlsoAppliesToACashOnDeliveryOrder`; FE `OrderDetailPage` "hides Cancel order and explains why once 48 hours have passed", `OrdersPage` "shows the server message and reloads the list when the window has already expired"; E2E-07 (happy path only: a browser cannot back-date an order) | — |
| FR-ORD-05 | `CancellationRulesTest.aCancellationLocksTheOrderThenRestoresStockPointsAndPaymentInOneUnit`, `…onlyPhysicalBooksGoBackToStockAndAnEbookIsNeverCounted`, `…pointsAreRestoredWithTheNumberRedeemedOnTheOrder`, `…thePaymentIsSettledOnceThroughThePaymentService`, `…aFailureWhileSettlingThePaymentPropagatesSoTheTransactionRollsBack`; `CancelOrderIT.cancellingACardOrderRefundsItAndRestoresStockAndPointsTogether`, `…aCashOnDeliveryOrderWithoutPointsHasItsPaymentCancelledAndNoLedgerRow`, `…anEBookIsNeverCountedInStockWhenAMixedOrderIsCancelled`, `…aFailureWhileSettlingThePaymentRollsBackTheWholeCancellation`, `…theDatabaseRefusesASecondRestoreRowForTheSameOrder`; `RewardRedemptionIT.redeemingThenCancellingGivesTheBalanceBackExactlyOnce`; E2E-07 (stock returns, payment cancelled or refunded), E2E-08 (points restored) | — |
| FR-ORD-06 | `CancellationRulesTest.anAlreadyCancelledOrderIsRejectedWithAlreadyCancelled`, `…anotherCustomersOrderIsNotFoundAndNothingChanges`; `CancelOrderIT.cancellingTwiceReportsAlreadyCancelledAndRestoresNothingTwice`, `…twoCancelsRacingForTheSameOrderYieldExactlyOneSuccessAndOneRestore`, `…anotherCustomersOrderAndAnUnknownOrderAreNotFound`; E2E-07 (second cancellation rejected, foreign order not found) | — |
| FR-ORD-07 | `ModuleBoundaryTest`; `api/openapi.yaml` has no modification, return or shipment operation (31 operations since AMD-002, `api/TRACEABILITY.md`); Banned strings | Review at T143 |
| FR-ORD-08 | `OrderServiceTest.anEbookOnlyOrderHasNoDeliveryAndNeverTouchesStock`; `PlaceOrderIT.aCardOrderIsPaidTakesStockDeductsPointsAndClearsTheBasket`, `…anEBookOnlyOrderHasNoDeliveryChargeAndTakesNoStock`; `StockConcurrencyIT` (six customers racing for the last copy give exactly one order, the loser is told out of stock, reversed basket order never deadlocks, no oversell with several copies per order); `CancelOrderIT` (stock restored) | — |

### Checkout (FR-CHK)

| ID | Automated test | Manual check |
|---|---|---|
| FR-CHK-01 | FE `CheckoutPage` "shows the basket, the address fields with India as the country, the summary and Pay Now", "shows an empty state instead of the form when the basket is empty"; `routes.test` (login first, then back to checkout); E2E-04 (guest sent to login and returned), E2E-05 | Wireframe comparison SCR-07 (T139) |
| FR-CHK-02 | FE `CheckoutPage` "has no e-mail, first-name, last-name or coupon field", `AddressesPage`; `AddressIT.creatingAnAddressStoresItForTheCallerWithIndiaAsTheDefaultCountry`; `AddressContractTest` | — |
| FR-CHK-03 | `AddressIT` (create, list oldest first, update, delete, owner only, `deletingASavedAddressDoesNotChangeAnOrderPlacedWithIt`); `PlaceOrderIT.anotherCustomersAddressCannotBeUsed`, `…exactlyOneAddressSourceIsRequired`; `OrderServiceTest.aSavedAddressIsCopiedIntoTheOrder`; FE `CheckoutPage` "explains that no address is saved when there are none", "fills the form from a saved address and lets it be edited", `AddressesPage` | — |
| FR-CHK-04 | `AddressIT.invalidPhoneAndPinCodeAreRejected`, `…theDatabaseRejectsAPhoneOrPinCodeThatIsNotAllDigits`, `…anUnknownIndianStateIsRejectedButAnyStateIsAcceptedForAnotherCountry`; `PlaceOrderIT.aNewAddressWithAnUnknownIndianStateIsRejected`; FE `validators.test` "address rules (FR-CHK-04)", `CheckoutPage` "blocks Pay Now with an inline error for each invalid field"; E2E-05 (checkout validates the address) | — |
| FR-CHK-05 | `PlaceOrderIT.theQuotePricesTheBasketAndStoresNothing`; `CheckoutContractTest`; FE `CheckoutPage` "shows the summary exactly as quoted", `CheckoutPage.rewards` (payable total updates with the points, on whole paise); Banned strings (no coupon text); the summary lines are asserted exactly in `CheckoutPage` "shows the summary exactly as quoted" | — |
| FR-CHK-06 | Banned strings; FE `CheckoutPage` "has no e-mail, first-name, last-name or coupon field"; `ModuleBoundaryTest.onlyTheApprovedModulePackagesExist` (no coupon module); no coupon table in `V1__init_schema.sql` (NFR-DAT-04) | — |
| FR-CHK-07 | FE `CheckoutPage.rewards` (balance, maximum, input, inline message, below the order summary); `RewardRedemptionIT.theQuoteShowsTheBalanceAndTheMaximumRedeemablePoints`; `RewardContractTest`; E2E-08 | — |
| FR-CHK-08 | FE `CheckoutPage` "moves on to payment with the address and the amount when everything is valid", "blocks Pay Now …"; `PaymentPage` "sends a visitor who did not come from checkout back to checkout"; E2E-05 | — |
| FR-CHK-09 | FE `CheckoutPage` "shows the basket, the address fields…" (asserts the Breadcrumb navigation region is present), `BookDetailPage` "shows a breadcrumb that follows the category hierarchy, and every level opens the catalogue there" | Click every breadcrumb link (T141) |

### Payment (FR-PAY)

| ID | Automated test | Manual check |
|---|---|---|
| FR-PAY-01 | FE `PaymentPage` "shows the title, the payable amount and exactly the four methods"; `PaymentServiceTest` (card, UPI and COD paths); E2E-05 (credit, debit, UPI, COD) | — |
| FR-PAY-02 | FE `PaymentPage` (exactly four methods); Banned strings (no Wallet); `BasketPage` "shows no wishlist, coupon or wallet control" | — |
| FR-PAY-03 | FE `PaymentPage` "shows the title, the payable amount…", "shows the form of the selected method only" | Wireframe comparison SCR-08 (T139) |
| FR-PAY-04 | `PaymentServiceTest.theDocumentedSampleCardIsApprovedForCreditAndDebit`, `…anyOtherWellFormedCardIsApproved`, `…theDeclineCardIsDeclinedAndTheAttemptIsRecordedAsFailed`, `…aCardThatExpiresThisMonthIsValidButLastMonthIsNot`; `PlaceOrderIT.anExpiredCardAndMismatchedPaymentDetailsAreValidationErrors`; FE `validators.test` "payment rules (PL-06)", `PaymentPage` "shows an inline error for each invalid field…", "rejects an expired card"; E2E-05, E2E-06 | — |
| FR-PAY-05 | `PaymentServiceTest.demoUpiIsApprovedAndOtherWellFormedIdsToo`, `…failureUpiIsDeclinedAndRecorded`; FE `PaymentPage` "blocks a blank or malformed UPI ID", "places a UPI order with the UPI ID only"; E2E-05, E2E-06 | — |
| FR-PAY-06 | `PaymentServiceTest.codIsPendingOnDeliveryWithoutAnyDetails`, `…codRejectsPaymentDetails`; `PlaceOrderIT.cashOnDeliveryIsPendingAndNeedsNoPaymentDetails`; `CancelOrderIT.theWindowAlsoAppliesToACashOnDeliveryOrder`; E2E-05 (COD skips the payment confirmation, payment pending), E2E-07 | — |
| FR-PAY-07 | `PaymentServiceTest` (decision is a local rule on documented test values); no HTTP client or gateway dependency in `backend/pom.xml` | Review at T143 |
| FR-PAY-08 | `PaymentServiceTest` (decline card and decline UPI ID are fixed, repeatable values); `PlaceOrderIT.aDeclinedCardChangesNothingButLeavesAFailedAttempt`, `…aDeclinedUpiIdFailsAndRetryingWithAGoodCardSucceeds`; E2E-06; test values listed in `docs/demo-script.md` (T142) | — |
| FR-PAY-09 | `PlaceOrderIT.aWrongExpectedTotalIsABasketChangedConflictAndNothingChanges`; `CheckoutContractTest`; FE `PaymentPage` "places the order with the card and the quoted amount"; E2E-05, E2E-08 (301.45 − points) | — |
| FR-PAY-10 | `OrderServiceTest.aDeclinedPaymentCreatesNoOrderAndChangesNothing`; `PlaceOrderIT.aDeclinedCardChangesNothingButLeavesAFailedAttempt`; FE `PaymentPage` "shows the generic decline message, clears the card fields, keeps the page and uses a new key for the retry", "lets the customer switch to another method after a decline"; E2E-06 (declined card, declined UPI, switch to COD) | — |
| FR-PAY-11 | `PlaceOrderIT.theSameKeyReplaysTheStoredOrderWithoutAnotherOrderOrStockMovement`, `…aMissingOrMalformedIdempotencyKeyIsRejected`, `…repeatingADeclinedKeyIsDeclinedAgainWithoutAnotherAttemptRow`; `OrderServiceTest.theSameKeyReturnsTheStoredOrderWithoutPayingAgain`; FE `PaymentPage` "disables Pay Now and the method choice while the payment is pending and sends one request", "keeps the same key after a network failure…" | — |
| FR-PAY-12 | `PaymentServiceTest.theStoredPaymentRowKeepsOnlyMethodStatusAmountAndKey`, `…aFailedAttemptIsRecordedWithoutCardDataAndWithoutAnOrder`; `PlaceOrderIT` (PAID, PENDING_ON_DELIVERY, FAILED with no order); `CancelOrderIT` (PAID becomes REFUNDED, COD pending becomes CANCELLED); database check constraints in `V1__init_schema.sql` | — |
| FR-PAY-13 | `PaymentServiceTest.thePaymentEntityHasNoFieldThatCouldHoldCardOrUpiData`, `…cardAndUpiDetailsAreMaskedWhenPrinted`, `…validationMessagesNeverEchoWhatWasTyped`; `PlaceOrderIT.cardDetailsAreNeverStoredOrEchoed`; `AuthServiceTest.requestsNeverPrintPasswords`; FE `PaymentPage` "writes nothing to local or session storage…", "does not show the CVV in clear text"; `SecurityRegressionIT.theLogsNeverContainPasswordsCardDataTokensOrTheSigningSecret` | — |

### Reward points (FR-RWD)

| ID | Automated test | Manual check |
|---|---|---|
| FR-RWD-01 | `RewardRedemptionIT.getRewardAccountReturnsOnlyTheCallersAccount`, `…theQuoteShowsTheBalanceAndTheMaximumRedeemablePoints`; `RewardContractTest`; FE `CheckoutPage.rewards`; E2E-08 | — |
| FR-RWD-02 | `RewardCapTest.redeemingExactlyTheCapIsAcceptedAndReducesThePayableTotalByOneRupeePerPoint`; `OrderServiceTest.redeemedPointsAreDeductedOnceForTheOrder`; FE `CheckoutPage.rewards` (`subtractPoints`); E2E-08 | — |
| FR-RWD-03 | **Limits:** `RewardCapTest` (balance below, equal to and above the cap, odd total rounds down, `…redeemingOnePointMoreThanTheCapIsRejectedWithPointsLimitExceeded`, `…thePointsCapNeverReachesTheWholeTotalSoAPaymentMethodIsAlwaysNeeded`); `PricingServiceTest.rewardCapIsHalfOfTheTotalRoundedDownAndNeverAboveTheBalance`; `OrderServiceTest.oneMorePointThanTheCapIsRejectedBeforePayment`, `…pointsAboveTheBalanceAreRejected`; `PlaceOrderIT.redeemingMoreThanHalfTheOrderOrMoreThanTheBalanceIsRefused`, `…theHalfCapIsInclusive`, `…theDatabaseRefusesAnOrderBeyondTheRewardCap`; `RewardRedemptionIT.redeemingExactlyTheCapSucceedsAndOnePointMoreIsRefusedWithNothingChanged`; FE `CheckoutPage.rewards` ("checkPoints"); E2E-08 (maximum accepted, one more rejected in the browser and by the server) | — |
| FR-RWD-04 | `OrderServiceTest.aDeclinedPaymentCreatesNoOrderAndChangesNothing`; `PlaceOrderIT.aDeclinedCardChangesNothingButLeavesAFailedAttempt`; `RewardRedemptionIT.theBalanceFallsByEachOrdersRedeemedPointsAndNeverBelowZero`, `…ordersRacingOnOneAccountNeverTakeTheBalanceBelowZero`, `…theDatabaseRefusesANegativeBalance` | — |
| FR-RWD-05 | `RewardCapTest.aZeroBalanceCannotRedeemAnything`, `…aZeroBalanceRejectsAnyRedemptionAndSaysSo`; `RewardRedemptionIT.aZeroBalanceAllowsNoRedemption`; FE `CheckoutPage.rewards` ("You have no reward points to redeem.") | — |
| FR-RWD-06 | `RewardServiceTest` (initial points from configuration, ledger rules); `AuthFlowIT.registerCreatesTheCustomerAndTheRewardAccountWithOneThousandPoints`; `RewardRedemptionIT.aNewAccountStartsWithOneThousandPoints`, `…pointsAreNeverEarnedFromPurchases`; E2E-08 (buying never earns points); no earn movement type in the schema | — |

### Confirmation (FR-CNF)

| ID | Automated test | Manual check |
|---|---|---|
| FR-CNF-01 | FE `PurchaseConfirmedPage` "shows the success message, the order reference and the purchased book", "shows the totals, the delivery address and the payment status"; E2E-05 | Wireframe comparison SCR-09 (T139) |
| FR-CNF-02 | FE `PurchaseConfirmedPage` "offers Continue your Shopping, which returns to the catalogue" | — |
| FR-CNF-03 | FE `PaymentPage` "places the order with the card… then opens the payment confirmation", "places a Cash on Delivery order… skips the payment confirmation", `PaymentConfirmedPage`, `PurchaseConfirmedPage` "shows a Cash on Delivery order with its payment as pending"; E2E-05 | — |
| FR-CNF-04 | `PlaceOrderIT.aCardOrderIsPaidTakesStockDeductsPointsAndClearsTheBasket`; `OrderHistoryIT.ordersAreListedNewestFirstWithTheirItemsAndPayableTotal`; E2E-05 (basket empty, order read back through the API), E2E-07 (order listed in My Orders) | — |
| FR-CNF-05 | FE `PurchaseConfirmedPage` "shows the success message, the order reference…"; E2E-05 | — |
| FR-CNF-06 | FE `PurchaseConfirmedPage` and `PaymentConfirmedPage` "only reads the order, so a refresh cannot create another one"; `PlaceOrderIT.theSameKeyReplaysTheStoredOrderWithoutAnotherOrderOrStockMovement` | Refresh and Back on the confirmation page (T141) |

## 2. Non-functional requirements

| ID | Automated test | Manual check or recorded evidence |
|---|---|---|
| NFR-SEC-01 | `AuthServiceTest.passwordIsStoredAsASaltedHashNeverInPlainText`; `SecurityComponentsTest.passwordsAreHashedWithSaltedPbkdf2AndNeverStoredInPlainText`; `SecurityRegressionIT.passwordsAreStoredOnlyAsSaltedHashes` | — |
| NFR-SEC-02 | `SecurityBaselineIT` (no cookie, tampered, expired, stale and unknown-customer tokens, CSRF missing or mismatching, no session, no HTTP Basic or form login); `SecurityComponentsTest` (token lifetime, signature, issuer); owner-only tests listed under FR-AUTH-07; `SecurityRegressionIT.everyProtectedOperationWithoutTheSessionCookieIsUnauthenticated`, `SecurityRegressionIT.everyStateChangingOperationWithoutAMatchingCsrfTokenIsRejectedAndChangesNothing`, `SecurityRegressionIT.theCookieOnlyReadsAreServedWithoutACsrfTokenAndMutatingCallsAreServedWithBoth` | — |
| NFR-SEC-03 | `BookygeekPropertiesTest.missingShortOrOverlongJwtSettingsStopStartUp`, `…corsOriginsMustBeExplicit`; `.env.example` holds placeholders only; `.gitignore` excludes `.env` | `git grep` for key patterns (T137) |
| NFR-SEC-04 | `PaymentServiceTest` (masking, no card fields on the entity); `PlaceOrderIT.cardDetailsAreNeverStoredOrEchoed`; `AuthServiceTest.requestsNeverPrintPasswords`; FE `PaymentPage` (no web storage); `SecurityRegressionIT.theLogsNeverContainPasswordsCardDataTokensOrTheSigningSecret` | — |
| NFR-SEC-05 | `CatalogueQueryIT.sqlInjectionTextInASearchIsTreatedAsPlainText`, `…invalidQueryParametersAreAValidationFailureWithNoDetailsLeaked`; `AuthFlowIT.invalidRegistrationListsEveryInvalidFieldAndCreatesNothing`; `AddressIT.invalidPhoneAndPinCodeAreRejected`; FE `BookCard.test` and `BookDetailPage` "renders text from the server as text, never as markup" | `dangerouslySetInnerHTML` check (T146) |
| NFR-SEC-06 | `GlobalExceptionHandlerTest.unexpectedFailureReturnsGenericInternalErrorWithoutDetails`, `…unreadableBodyBecomesValidationFailedWithoutLeakingParserDetails`, `…everyErrorCodeMapsToItsContractStatusAndHasAGenericMessage`; `BookDetailIT.theErrorBodyNeverRevealsInternals`; `AuthFlowIT.errorBodiesNeverRevealInternals`; `SecurityBaselineIT.unmatchedRouteIsAGenericNotFoundApiErrorWithoutDetails` | — |
| NFR-SEC-07 | — | Dependency scans, recorded in `docs/security-scan-results.md` (T136) |
| NFR-DAT-01 | `OrderHistoryIT`, `PlaceOrderIT`, `CancelOrderIT`, `RewardRedemptionIT` (after each journey the order, stock, payment and balance agree); E2E-05, E2E-07, E2E-08 | — |
| NFR-DAT-02 | `CancelOrderIT.aFailureWhileSettlingThePaymentRollsBackTheWholeCancellation`; `CancellationRulesTest.aFailureWhileSettlingThePaymentPropagatesSoTheTransactionRollsBack`; `PlaceOrderIT.aDeclinedCardChangesNothingButLeavesAFailedAttempt`; `StockConcurrencyIT` | — |
| NFR-DAT-03 | `PlaceOrderIT` and `CancelOrderIT` (payment state after placement and after cancellation); `CancelOrderIT.theDatabaseRefusesACancelledOrderWithoutACancellationTimeAndTheOtherWayRound`; check constraints in `V1__init_schema.sql` | — |
| NFR-DAT-04 | `ModuleBoundaryTest.onlyTheApprovedModulePackagesExist`; Banned strings | Data-model approval is recorded in `specs/001-bookygeek-platform/data-model.md`; seed data is reproducible (fixed ids in `db/seed/R__seed_books.sql`; `IntegrationTestBase.restoreStockAfterTest` returns stock to the seed baseline after every test) |
| NFR-DAT-05 | Every IT starts on a database built by Flyway with `spring.jpa.hibernate.ddl-auto=validate` (`application.yml`) | Clean-database build is repeated in T135 and T141 |
| NFR-API-01 | All `*ContractTest` classes (status codes per operation); `SecurityBaselineIT` | — |
| NFR-API-02 | All `*ContractTest` classes (`ApiError` shape); `GlobalExceptionHandlerTest.errorCodesMatchTheApprovedContractExactly` | — |
| NFR-API-03 | Negative tests in `BasketIT.anUnknownBookIsNotFoundAndInvalidBodiesAreRejected`, `…previewRejectsAnEmptyOrOversizedRequest`, `AddressIT`, `AuthFlowIT`, `PlaceOrderIT`, `CatalogueQueryIT.invalidQueryParametersAreAValidationFailureWithNoDetailsLeaked` | — |
| NFR-API-04 | `api/TRACEABILITY.md` (31 operations, controller, contract test, integration test, all implemented); T133 checked that the controllers exposed exactly the original 29 contract operations; `ReviewIT` covers the 2 operations added by AMD-002 | Contract approved at gate G-API (2026-11-14). It was validated with command-line validators only; the hosted Swagger Editor log was not produced (`plan.md` Gate Register) |
| NFR-FE-01 | FE `useAsync.test`, `common.test` (`Spinner`, `ErrorMessage`, `EmptyState`), `CataloguePage` (loading then error with Retry), `BookDetailPage`, `BasketPage`, `CheckoutPage`, `OrdersPage`, `PurchaseConfirmedPage` ("shows an error with a retry when the order cannot be loaded") | Review at T146 |
| NFR-FE-02 | FE `validators.test`, `LoginPage`, `RegisterPage`, `CheckoutPage`, `AddressesPage`, `PaymentPage` (invalid input blocked, API not called) | — |
| NFR-FE-03 | `common.test` (shared components) | Code review recorded in `docs/frontend-code-review.md` (T146, Phase 11) |
| NFR-FE-04 | Overflow sweep: 14 screens at 1280, 1024, 1023, 640, 639 and 375 px, 0 of 84 overflow (`docs/nfr-verification.md` section 5) | Filter row is not behind a toggle on mobile and the catalogue has two columns at 1024 px (recorded in section 5) |
| NFR-TST-01 | This document. Rule to test: cancellation boundary → FR-ORD-04; reward limits → FR-RWD-03; pricing → FR-BSK-05; stock and basket limits → FR-BSK-01 and FR-ORD-08; Buy Again → FR-ORD-03; payment methods and simulated failures → FR-PAY-04..06, FR-PAY-08; address validation → FR-CHK-04; unavailable products → FR-CAT-08 | — |
| NFR-TST-02 | `mvn clean verify` (JUnit 5, Mockito, Spring Boot Test) | Result recorded in T135 |
| NFR-TST-03 | `npm test` (Vitest, React Testing Library) | Result recorded in T135 |
| NFR-TST-04 | E2E-01 to E2E-09 (`e2e/tests`) | Result recorded in T135 (Chromium only so far) |
| NFR-TST-05 | — | Defects found during the build are listed in the STATUS notes of `tasks.md`; none are open |
| NFR-MNT-01 | `ModuleBoundaryTest` (module boundaries, layer naming, controllers do not touch repositories) | Code review (T146) |
| NFR-MNT-02 | — | `README.md` (T140), `api/TRACEABILITY.md`, `docs/` |
| NFR-LOC-01 | — | `docker compose up` run end to end (T141); not run yet, see below |
| NFR-LOC-02 | — | Written instructions in `README.md` and `quickstart.md` (T140, T141) |
| NFR-LOC-03 | `BookygeekPropertiesTest.configuredValuesOverrideDefaults` (configuration comes from the environment) | Optional deployment notes (T144) |
| NFR-LOC-04 | — | `README.md` and `.env.example` (T140) |
| NFR-PERF-01 | All 29 operations timed, slowest 126.8 ms (`docs/nfr-verification.md` section 2) | Compose stack not measured (Docker unavailable) |
| NFR-PERF-02 | Initial load, production build: load event max 52.6 ms, first card max 161 ms; dev server max 288 ms (`docs/nfr-verification.md` section 3) | Compose nginx stack not measured (Docker unavailable) |
| NFR-BRW-01 | E2E-01 to E2E-08 (31 tests): Chromium, Microsoft Edge 157, Firefox and WebKit all 31 of 31 (`docs/nfr-verification.md` section 6) | Google Chrome not installed; Firefox and WebKit not re-run after the two CSS fixes |
| NFR-ACC-01 | FE tests query controls by label (`getByLabelText`) and role; Banned strings checks `aria-label`, `alt` and `title`; custom audit of 14 screens (0 unlabelled controls, 0 missing alt, 0 contrast failures, min 4.81:1); keyboard-only journey, 10 of 10 steps (`docs/nfr-verification.md` section 4) | No axe-core or screen-reader test; no skip link; not-found page has no `<h1>` |

## 3. Architecture and exclusion tests

| Test | What it enforces |
|---|---|
| `ModuleBoundaryTest.onlyTheApprovedModulePackagesExist` | Only the seven business modules and `common` exist. |
| `ModuleBoundaryTest.noClassUsesAnotherModulesRepositoryOrEntity` | No module reads another module's repository or entity. |
| `ModuleBoundaryTest.onlyCatalogueAndCategoryMayDependOnEachOther` | The only mutual service dependency is catalogue and category. |
| `ModuleBoundaryTest.controllersDoNotTouchRepositories`, `…servicesAreConcreteClasses`, `…layerPackagesFollowTheNamingConvention` | Layering and naming. |
| `ModuleBoundaryTest.theRulesDetectViolationsInThisVerySuite` | The rules themselves are proven to catch a violation. |
| `bannedStrings.test.jsx` "excluded features are never shown" | Rendered text, `aria-label`, `alt` and `title` on every route (guest and signed in) never match: "bookworm", "wishlist", "my writers", "coupon", "wallet", "bestsellers", "new launches". "review", "rating" and "writer" strings are allowed since AMD-002. |

## 4. Not yet verified

These are the gaps. Each is a task that is still open or an environment limit. None is claimed as passed.

| Gap | Reason | Where it is closed |
|---|---|---|
| Docker Compose stack (`postgres-test`, `docker compose up`, `E2E_BASE_URL=http://127.0.0.1:3000`) | Docker and Podman are not installed on the authoring machine. All tests ran against a throw-away local PostgreSQL 18.6 on 127.0.0.1:5433. | T014, T046, T135, T141 on a machine with Docker or Podman |
| Google Chrome run | Chrome is not installed. Chromium, Edge 157, Firefox and WebKit each passed 31 of 31 E2E tests (`docs/nfr-verification.md` section 6). | Owner, if Chrome itself is required |
| Log inspection beyond the exercised paths | `SecurityRegressionIT` captures the log output of failed logins, declined and successful card payments, an unreadable request and a forced 500 (an `ApiException`, not logged by the handler). Log lines of the `Unexpected error` handler are not covered. | T138 (limit) |
| Wireframe match for SCR-07, SCR-08, SCR-09 and RSP-01/RSP-03 | Collected in `docs/nfr-verification.md` sections 5 and 7: no illustrated background on SCR-08 and SCR-09, two-column checkout, filter row not behind a toggle on mobile. SC-003 is not claimed as met. | Owner decision |
| Dependency scan, container review | Not run yet. | T136, T137 |
| `listOrders` paging | The approved contract declares no paging, so none was built. Owner decision. | Owner |
