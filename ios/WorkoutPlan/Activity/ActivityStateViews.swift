import SwiftUI
import UIKit

/// Health access has not been asked for yet (for example "Not now" on first launch).
struct NoHealthAccessView: View {
    let connect: () async -> Void
    @State private var isConnecting = false

    var body: some View {
        ContentUnavailableView {
            Label("Connect Apple Health", systemImage: "heart.text.square")
        } description: {
            Text("Workout Plan reads your activity, sleep and heart data from Apple Health. Nothing leaves your iPhone.")
        } actions: {
            Button {
                isConnecting = true
                Task {
                    await connect()
                    isConnecting = false
                }
            } label: {
                Text("Connect Apple Health")
                    .frame(minHeight: TouchTarget.minimum - 16)
            }
            .buttonStyle(.borderedProminent)
            .disabled(isConnecting)
        }
        .frame(maxWidth: .infinity, minHeight: 320)
    }
}

/// Health is not available on this device.
struct HealthUnavailableView: View {
    var body: some View {
        ContentUnavailableView(
            "Apple Health not available",
            systemImage: "heart.slash",
            description: Text("This device can't read Apple Health data.")
        )
        .frame(maxWidth: .infinity, minHeight: 320)
    }
}

/// Health returned nothing: no Watch data yet, or read access is off.
struct NoActivityDataView: View {
    var body: some View {
        ContentUnavailableView {
            Label("No data yet", systemImage: "applewatch")
        } description: {
            Text("Wear your Apple Watch and your activity, sleep and heart data will show here. If you turned off access, open Settings › Privacy & Security › Health › Workout Plan.")
        } actions: {
            OpenSettingsButton()
        }
        .frame(maxWidth: .infinity, minHeight: 320)
    }
}

/// A query failed.
struct ActivityErrorView: View {
    let message: String
    let retry: () async -> Void

    var body: some View {
        ContentUnavailableView {
            Label("Couldn't read Apple Health", systemImage: "exclamationmark.triangle")
        } description: {
            Text(message)
        } actions: {
            Button("Try Again") {
                Task { await retry() }
            }
            .buttonStyle(.bordered)
        }
        .frame(maxWidth: .infinity, minHeight: 320)
    }
}

/// Shown when only about 30 days of history are readable.
struct LimitedHistoryBanner: View {
    var body: some View {
        HStack(alignment: .top, spacing: Spacing.s) {
            Image(systemName: "clock.arrow.circlepath")
                .font(AppFont.headline)
                .foregroundStyle(Color.appTint)
                .accessibilityHidden(true)
            VStack(alignment: .leading, spacing: Spacing.xxs) {
                Text("Only 30 days of history shared")
                    .font(AppFont.headline)
                    .foregroundStyle(Color.appLabel)
                Text("Your yearly review needs more. Open Settings › Privacy & Security › Health › Workout Plan and choose All Recorded Data.")
                    .font(AppFont.subheadline)
                    .foregroundStyle(Color.appSecondaryLabel)
                    .fixedSize(horizontal: false, vertical: true)
                OpenSettingsButton()
                    .padding(.top, Spacing.xxs)
            }
        }
        .padding(Spacing.m)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color.appCard, in: RoundedRectangle(cornerRadius: Radius.tile, style: .continuous))
        .accessibilityElement(children: .contain)
    }
}

/// Opens this app's page in Settings. Health access itself lives under
/// Privacy & Security › Health; iOS has no public link straight there.
struct OpenSettingsButton: View {
    @Environment(\.openURL) private var openURL

    var body: some View {
        Button("Open Settings") {
            if let url = URL(string: UIApplication.openSettingsURLString) {
                openURL(url)
            }
        }
        .font(AppFont.subheadline.weight(.semibold))
        .frame(minHeight: TouchTarget.minimum)
    }
}
