-- Somente leitura: comparar esta saida na origem e no destino isolado.
SELECT 'migrations' AS item, count(*) AS total FROM flyway_schema_history WHERE success
UNION ALL SELECT 'users', count(*) FROM app_user
UNION ALL SELECT 'admin_memberships', count(*) FROM admin_company_membership
UNION ALL SELECT 'companies', count(*) FROM empresa
UNION ALL SELECT 'courts', count(*) FROM quadra
UNION ALL SELECT 'devices', count(*) FROM dispositivo
UNION ALL SELECT 'replays', count(*) FROM replay
UNION ALL SELECT 'library', count(*) FROM library_entry
UNION ALL SELECT 'purge_jobs', count(*) FROM purge_job
UNION ALL SELECT 'reconciliation', count(*) FROM reconciliation_issue
UNION ALL SELECT 'installation_health', count(*) FROM installation_health
ORDER BY item;
SELECT count(*) AS invalid_origin FROM replay r JOIN dispositivo d ON d.id=r.dispositivo_id
JOIN quadra q ON q.id=d.quadra_id WHERE r.quadra_id<>q.id OR r.empresa_id<>q.empresa_id;
SELECT count(*) AS broken_library FROM library_entry b JOIN replay r ON r.evento_id=b.event_id
LEFT JOIN replay_lifecycle l ON l.event_id=r.evento_id WHERE r.purged_at IS NOT NULL OR l.purge_reserved_at IS NOT NULL;
SELECT state,count(*) FROM purge_job GROUP BY state ORDER BY state;
