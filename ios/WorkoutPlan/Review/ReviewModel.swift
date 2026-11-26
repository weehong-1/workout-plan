import Foundation
import Observation

@MainActor
@Observable
final class ReviewModel {
    private(set) var period: ReviewPeriod
    private(set) var data: ReviewData?
    private(set) var links: [ShareLinkSummary] = []
    private(set) var isLoading = false
    var errorMessage: String?
    var linksMessage: String?

    private let services: AppServices
    private let calendar: Calendar

    init(services: AppServices, calendar: Calendar = .current) {
        self.services = services
        self.calendar = calendar
        self.period = ReviewPeriod(kind: .month, containing: Date(), calendar: calendar)
    }

    var loader: ReviewLoader {
        ReviewLoader(health: services.health, weight: services.weight, food: services.food, calendar: calendar)
    }

    var linkService: TrainerLinkService? {
        guard let client = services.account.client else { return nil }
        return TrainerLinkService(client: client, loader: loader, photoSources: (services.weight, services.food))
    }

    var hasNext: Bool { period.hasNext(now: Date(), calendar: calendar) }

    func setKind(_ kind: ReviewKind) async {
        period = period.switched(to: kind, now: Date(), calendar: calendar)
        await load()
    }

    func move(by count: Int) async {
        let next = period.shifted(by: count, calendar: calendar)
        guard next.start <= Date() else { return }
        period = next
        await load()
    }

    func load() async {
        isLoading = true
        defer { isLoading = false }
        let requested = period
        do {
            let result = try await loader.load(requested, now: Date())
            guard requested == period else { return }
            data = result
            errorMessage = nil
        } catch {
            errorMessage = error.localizedDescription
        }
    }

    func loadLinks() async {
        await services.account.refresh()
        guard services.account.isSignedIn, let service = linkService else {
            links = []
            return
        }
        do {
            links = try await service.list()
            linksMessage = nil
        } catch {
            linksMessage = "Couldn't load your links: \(error.localizedDescription)"
        }
    }

    func revoke(_ link: ShareLinkSummary) async {
        guard let service = linkService else { return }
        do {
            try await service.revoke(link)
            links.removeAll { $0.id == link.id }
        } catch {
            linksMessage = "Couldn't revoke: \(error.localizedDescription)"
        }
    }
}
