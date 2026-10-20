import 'dart:async';

import 'package:flutter/material.dart';
import 'package:immich_mobile/extensions/build_context_extensions.dart';
import 'package:immich_mobile/extensions/theme_extensions.dart';
import 'package:immich_mobile/generated/translations.g.dart';
import 'package:immich_mobile/providers/infrastructure/platform.provider.dart';

/// Settings tile for displaying hardcoded trusted domains (read-only).
/// The list is defined in HttpClientManager.kt and cannot be modified from UI.
class TrustedDomainsSettings extends StatefulWidget {
  const TrustedDomainsSettings({super.key});

  @override
  State<TrustedDomainsSettings> createState() => _TrustedDomainsSettingsState();
}

class _TrustedDomainsSettingsState extends State<TrustedDomainsSettings> {
  List<String> _trustedDomains = [];
  bool _isLoading = true;

  @override
  void initState() {
    super.initState();
    unawaited(_loadTrustedDomains());
  }

  Future<void> _loadTrustedDomains() async {
    try {
      final domains = await networkApi.getTrustedDomains();
      if (mounted) {
        setState(() {
          _trustedDomains = domains;
          _isLoading = false;
        });
      }
    } catch (e) {
      if (mounted) {
        setState(() {
          _isLoading = false;
        });
      }
    }
  }

  @override
  Widget build(BuildContext context) {
    final translations = context.t;
    final theme = context.colorScheme;
    final textTheme = context.textTheme;
    final emptyMessage = translations.trustedDomainsEmpty;
    final emptyHint = translations.trustedDomainsEmptyHint;

    return ListTile(
      contentPadding: EdgeInsets.zero,
      horizontalTitleGap: 16,
      isThreeLine: true,
      title: Text(translations.trustedDomainsTitle, style: textTheme.bodyLarge?.copyWith(fontWeight: FontWeight.w500)),
      subtitle: Column(
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Text(
            translations.trustedDomainsSubtitle,
            style: textTheme.bodyMedium?.copyWith(color: theme.onSurfaceSecondary),
          ),
          const SizedBox(height: 8),
          _buildDomainsList(context, translations, theme, textTheme, emptyMessage, emptyHint),
        ],
      ),
    );
  }

  Widget _buildDomainsList(
    BuildContext context,
    Translations translations,
    ColorScheme theme,
    TextTheme textTheme,
    String emptyMessage,
    String emptyHint,
  ) {
    if (_isLoading) {
      return const SizedBox(
        height: 48,
        child: Center(child: SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2))),
      );
    }

    if (_trustedDomains.isEmpty) {
      return _buildEmptyState(context, translations, theme, textTheme, emptyMessage, emptyHint);
    }

    return _buildDomainsListWidget(context, translations, theme, textTheme);
  }

  Widget _buildEmptyState(
    BuildContext context,
    Translations translations,
    ColorScheme theme,
    TextTheme textTheme,
    String message,
    String hint,
  ) {
    return Padding(
      padding: const EdgeInsets.only(top: 4),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.symmetric(horizontal: 12, vertical: 8),
        decoration: BoxDecoration(color: theme.surfaceContainerHighest, borderRadius: BorderRadius.circular(8)),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                Icon(Icons.info_outline, size: 16, color: theme.primary),
                const SizedBox(width: 8),
                Expanded(
                  child: Text(message, style: textTheme.bodySmall?.copyWith(color: theme.primary)),
                ),
              ],
            ),
            const SizedBox(height: 4),
            Text(hint, style: textTheme.bodySmall?.copyWith(color: theme.onSurfaceVariant)),
          ],
        ),
      ),
    );
  }

  Widget _buildDomainsListWidget(
    BuildContext context,
    Translations translations,
    ColorScheme theme,
    TextTheme textTheme,
  ) {
    return Padding(
      padding: const EdgeInsets.only(top: 4),
      child: Container(
        width: double.infinity,
        padding: const EdgeInsets.all(12),
        decoration: BoxDecoration(color: theme.surfaceContainerHighest, borderRadius: BorderRadius.circular(8)),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          mainAxisSize: MainAxisSize.min,
          children: [
            Row(
              children: [
                Icon(Icons.check_circle_outline, size: 16, color: theme.primary),
                const SizedBox(width: 8),
                Text(
                  translations.trustedDomainsFound(k0: _trustedDomains.length),
                  style: textTheme.bodySmall?.copyWith(color: theme.primary, fontWeight: FontWeight.w500),
                ),
              ],
            ),
            const SizedBox(height: 8),
            SingleChildScrollView(
              scrollDirection: Axis.horizontal,
              child: Wrap(
                spacing: 6,
                runSpacing: 4,
                children: _trustedDomains.map((domain) {
                  return Chip(
                    label: Text(domain, style: textTheme.bodySmall?.copyWith(color: theme.onSurface)),
                    visualDensity: VisualDensity.compact,
                    materialTapTargetSize: MaterialTapTargetSize.shrinkWrap,
                    side: BorderSide.none,
                  );
                }).toList(),
              ),
            ),
          ],
        ),
      ),
    );
  }
}
