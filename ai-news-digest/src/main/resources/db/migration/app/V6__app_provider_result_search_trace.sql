-- Searches of a research call and the pages they returned (diagnosis of the research quality,
-- provider.SearchTrace as JSON). NULL if the model did not search. Same retention as the row.
ALTER TABLE provider_result ADD COLUMN search_trace jsonb;

COMMENT ON COLUMN provider_result.search_trace IS
    'Web searches of the research call: queries and returned pages (diagnosis), NULL without search';
