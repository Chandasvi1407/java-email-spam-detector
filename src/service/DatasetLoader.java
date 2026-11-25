package service;

import ai.NaiveBayesSpamDetector;
import model.Email;
import model.Label;

import java.util.ArrayList;
import java.util.List;

/**
 * Provides the built-in training corpus of 40 realistic SPAM and HAM emails
 * and manages appending new user-labeled training samples at runtime.
 */
public class DatasetLoader {

    public record LabeledEmail(Email email, Label label) {
    }

    private final List<LabeledEmail> samples = new ArrayList<>();

    public DatasetLoader() {
        seedDefaultDataset();
    }

    /**
     * Trains the given {@link NaiveBayesSpamDetector} on all currently loaded samples.
     *
     * @param detector Naive Bayes detector instance to populate
     */
    public synchronized void loadInto(NaiveBayesSpamDetector detector) {
        if (detector == null) {
            throw new IllegalArgumentException("detector must not be null");
        }
        for (LabeledEmail sample : samples) {
            detector.train(sample.email(), sample.label());
        }
    }

    /**
     * Appends a new user-labeled email to the in-memory corpus and updates the live model.
     *
     * @param email    user-submitted email
     * @param label    user-assigned ground truth label
     * @param detector live Naive Bayes model to retrain incrementally
     */
    public synchronized void appendTrainingSample(Email email, Label label, NaiveBayesSpamDetector detector) {
        if (email == null || label == null || detector == null) {
            throw new IllegalArgumentException("email, label, and detector must not be null");
        }
        samples.add(new LabeledEmail(email, label));
        detector.train(email, label);
    }

    public synchronized List<LabeledEmail> getSamples() {
        return List.copyOf(samples);
    }

    private void seedDefaultDataset() {
        // --- 20 Realistic SPAM Emails ---
        addSpam("security-alert@paypa1-verify.xyz",
                "URGENT: Your Account Has Been Suspended!!",
                "Dear Customer, Unusual activity was detected on your account. Verify your account immediately at http://bit.ly/verify-now-99 or your funds will be frozen within 24 hours!!!");

        addSpam("claims@global-lottery-prize.win",
                "CONGRATULATIONS YOU WON $2,500,000.00 JACKPOT!!!",
                "You are our lucky winner! Claim your prize cash payout now by sending your bank details and processing fee. Act now limited time offer!!!");

        addSpam("crypto-wealth@fastprofits.top",
                "Double Your Bitcoin in 48 Hours - GUARANTEED 100% FREE",
                "Join our exclusive crypto trading bot! Turn $250 into $10,000 risk free. Click here https://fast-crypto-roi.top/join to start earning passive income today!!");

        addSpam("hr-director@wire-settlements.click",
                "Urgent Wire Transfer Request - Confidential",
                "Please process an immediate wire transfer of $18,450 to our new beneficiary account before 3 PM today. Do not call, confirm via reply email.");

        addSpam("deals@discount-pharmacy-rx.xyz",
                "Save 85% on Pharmacy Meds - No Prescription Needed!",
                "100% free shipping worldwide! Buy cheap viagra and wellness supplements at unbeatable discount prices. Click here www.rx-mega-deals.xyz");

        addSpam("noreply-prize@reward-center.tk",
                "You Have 1 Unclaimed Amazon $1000 Gift Card!",
                "CONGRATULATIONS! Your email was selected for a FREE $1000 gift card bonus. Claim now before midnight: http://tinyurl.com/free-gift-2026");

        addSpam("support@microsoft-security-help.gq",
                "CRITICAL ALERT: Password Reset Required Immediately",
                "Your mailbox password will expire today. Unauthorized login attempt from unknown IP. Click here to reset your password now: http://198.51.100.44/login");

        addSpam("refunds@irs-tax-rebate.click",
                "Federal Tax Refund Pending - Action Required",
                "You are eligible for a $1,480.00 tax refund. Submit your social security number and bank details at http://irs-claim-portal.click to receive instant deposit.");

        addSpam("prince.adebayo@royal-trust-lagos.ml",
                "Confidential Inheritance Proposal - $14.5 Million USD",
                "Dear Friend, I am seeking a trustworthy foreign beneficiary to transfer $14.5 million inheritance funds. You will receive 30% share. Reply with full name and bank account.");

        addSpam("delivery@express-parcel-tracking.top",
                "Package Held at Customs - Pay $2.99 Release Fee",
                "Your parcel could not be delivered due to unpaid customs duty. Click here http://rb.gy/track-fee to pay immediately or package will be returned.");

        addSpam("growth@seo-ranking-boost.com",
                "Get #1 on Google Overnight - 100% Guaranteed Results!",
                "Struggling with website traffic? Buy 50,000 backlinks for only $49! Limited time discount. Reply YES or unsubscribe here.");

        addSpam("recruiter@remote-cash-jobs.xyz",
                "Earn $500/Day Working From Home - No Experience!",
                "URGENT HIRING! Make easy cash stuffing envelopes and reviewing products online. Guaranteed weekly payout! Sign up free at http://work-easy-cash.xyz");

        addSpam("alerts@chase-online-protect.top",
                "Unauthorized Wire Transfer Blocked - Verify Identity",
                "We detected an unauthorized login. Please verify your account credentials and PIN at https://chase-verify-secure.top/auth within 2 hours.");

        addSpam("vip@casino-royale-spin.win",
                "200 FREE SPINS + $5000 Welcome Bonus Waiting!!",
                "Ready to hit the jackpot? Deposit $20 and get 500% instant bonus cash! Play slots and win real money risk free today!!!");

        addSpam("billing@norton-autorenew-invoice.click",
                "Invoice #88412: $499.99 Auto-Renewal Charged",
                "Your annual antivirus subscription has been renewed for $499.99. If you did not authorize this charge, call +1-800-555-0199 immediately for a refund.");

        addSpam("marketing@luxury-watches-outlet.xyz",
                "Replica Luxury Watches 90% OFF Flash Sale!!",
                "Authentic look designer watches starting at $79! Free express delivery and 100% satisfaction guaranteed. Shop now: http://watches-flash.xyz");

        addSpam("investor@penny-stock-alerts.top",
                "HOT STOCK PICK SET TO EXPLODE 1000% THIS WEEK",
                "Insider tip: Buy ticker XYZZ before market open tomorrow! Guaranteed millionaire maker opportunity. Act now before wall street finds out!");

        addSpam("admin@webmail-quota-exceeded.tk",
                "Mailbox Quota 99% Full - Upgrade Free Storage",
                "Your corporate email storage is almost full. Click here http://mail-upgrade-portal.tk to keep receiving messages or your account will be suspended.");

        addSpam("loans@instant-approval-cash.loan",
                "Bad Credit? Get $25,000 Cash Loan Approved in 5 Mins!",
                "Zero credit check! Instant cash transfer directly to your checking account. Apply now risk free at https://fast-loan-cash.loan");

        addSpam("promo@miracle-weightloss-2026.click",
                "Lose 30 Pounds in 2 Weeks Without Exercise!!",
                "Doctors shocked by this secret pill! Order your free trial bottle today and pay only shipping. Limited supply act now!!!");

        // --- 20 Realistic HAM Emails ---
        addHam("sarah.chen@acmecorp.com",
                "Q4 Product Roadmap Review - Thursday 2:00 PM",
                "Hi team, Please review the updated Q4 engineering milestones before Thursday's planning meeting. I attached the sprint capacity breakdown in our shared drive.");

        addHam("notifications@github.com",
                "[acmecorp/backend-api] Pull Request #412 merged by alex-dev",
                "alex-dev merged 3 commits into main from feature/rate-limiter. All 48 unit tests and integration checks passed on CI.");

        addHam("billing@aws.amazon.com",
                "Your AWS Invoice for October 2026 is Available",
                "Hello, Your monthly Amazon Web Services statement for account ending in 4821 is ready. Total charges for this billing period: $142.68.");

        addHam("appointments@brightsmile-dental.com",
                "Appointment Reminder: Dental Cleaning on Friday at 10:30 AM",
                "Hi Jordan, This is a friendly reminder of your upcoming routine checkup with Dr. Patel this Friday at 10:30 AM. Let us know if you need to reschedule.");

        addHam("david.miller@acmecorp.com",
                "Re: Database migration script feedback",
                "Thanks for catching that index issue on the users table. I pushed an updated migration file and tested it against staging. Everything looks clean now.");

        addHam("receipts@uber.com",
                "Your Tuesday morning trip receipt",
                "Thanks for riding with Uber. Trip duration: 18 minutes, Distance: 6.4 miles. Total charged to Visa ending in 1904: $21.35.");

        addHam("registrar@stateuniversity.edu",
                "Spring Semester Course Registration Opens Monday",
                "Dear Students, Course registration for the Spring 2027 term opens on Monday at 8:00 AM. Please meet with your academic advisor to clear any advising holds.");

        addHam("emma.watson@designstudio.io",
                "Updated Figma wireframes for the analytics dashboard",
                "Hey! I updated the color contrast on the sidebar navigation and added the responsive tablet breakpoints. Let me know what you think during tomorrow's standup.");

        addHam("reservations@delta.com",
                "Flight Confirmation: DL 1428 (SFO to SEA)",
                "Your flight itinerary is confirmed for November 28. Departure: 8:15 AM from San Francisco Terminal 1. Boarding pass will be available 24 hours before departure.");

        addHam("support@atlassian.com",
                "Ticket #SUP-9042 Updated: SSO Configuration Question",
                "Hi there, Our solutions engineer reviewed your SAML metadata XML and noted that the Entity ID matches your staging tenant. Reply to this thread if you need a quick call.");

        addHam("alex.rivera@acmecorp.com",
                "Lunch order for Friday's sprint retrospective?",
                "Hey everyone, We are ordering Mediterranean bowls for Friday's retrospective at 12:30 PM. Please add your dietary preferences to the sheet by tomorrow afternoon.");

        addHam("newsletter@pragmaticengineer.com",
                "Issue #214: How Teams Scale Observability Pipelines",
                "In this week's engineering deep dive, we examine how mid-sized infrastructure teams balance OpenTelemetry sampling costs against debugging fidelity.");

        addHam("hr@acmecorp.com",
                "Annual Benefits Open Enrollment Reminder",
                "Hello team, Open enrollment for health, dental, and FSA plans closes next Wednesday. Visit the employee portal if you wish to make changes for next year.");

        addHam("orders@bookshop.org",
                "Your order #BK-77310 has shipped",
                "Good news! Your copy of 'Designing Data-Intensive Applications' is on its way via USPS Media Mail and is expected to arrive within 3 to 5 business days.");

        addHam("mom@familymail.net",
                "Sunday dinner plans and grocery list",
                "Hi sweetie, Are you still coming over around 5 PM on Sunday? Dad is making roasted vegetables and salmon. Let me know if you want to bring dessert.");

        addHam("devops@acmecorp.com",
                "Scheduled Maintenance Window: Staging Cluster Tonight 11 PM",
                "FYI: We will be applying PostgreSQL minor version patches to the staging environment tonight between 11:00 PM and 11:30 PM PST. Production will not be affected.");

        addHam("calendar-noreply@google.com",
                "Invitation: Architecture Sync @ Tue 3:00 PM - 3:45 PM",
                "Marcus invited you to Architecture Sync. Agenda: Review caching strategy for the search service and finalize API response schemas.");

        addHam("library@citypubliclibrary.org",
                "Item Ready for Pickup: Clean Architecture",
                "Your requested hold item is now available at the Central Branch pickup shelf and will be held through Tuesday next week.");

        addHam("ci-bot@gitlab.com",
                "Pipeline #18923 passed for main (commit 9a4f21c)",
                "Project: customer-portal. Stage test: 112 passed, 0 failed. Stage build: Docker image tagged and pushed to internal registry.");

        addHam("accounting@acmecorp.com",
                "October Expense Reports Due by Friday",
                "Hi all, Please submit any remaining October travel or software expense receipts in Expensify by Friday 5:00 PM so we can close the monthly books on time.");
    }

    private void addSpam(String sender, String subject, String body) {
        samples.add(new LabeledEmail(new Email(sender, subject, body), Label.SPAM));
    }

    private void addHam(String sender, String subject, String body) {
        samples.add(new LabeledEmail(new Email(sender, subject, body), Label.HAM));
    }
}
