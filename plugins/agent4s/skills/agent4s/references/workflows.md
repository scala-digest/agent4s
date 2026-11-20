# Working with a Scala project

Run each query with the bundled launcher in the target repository or pass its root with `-w`. Choose the steps that answer the user's question.

## Explore an unfamiliar checkout

Start with `overview --concise` for the package names and frequently extended types. Use `entrypoints --no-tests` to find where application code starts. Pick an entry point, read its body and follow the types it uses with `explain Type --related`.

## Assess the impact of a change

Locate the declaration with `def Type --verbose`. Use `impl Type` for an interface change, or `call-graph method --in Owner` for a method change. `refs Type --top 10` points you to files with the most name matches. Read the affected source lines and the tests returned by `coverage Type`.

For a package boundary, use `api com.example --used-by com.consumer` to see which symbols the consumer imports. `summary com.example` gives the subpackage structure.

## Rename a symbol

1. Use `def OldName --verbose` to identify the intended declaration. Check its package and owner if there are several matches.
2. Run `rename OldName NewName` to preview. If the project has current SemanticDB output, use `--semantic` and read stderr to see which mode the engine used.
3. Inspect the old and new lines. If the user requested the change and the preview matches its scope, repeat the command with `--apply`. Edit individual lines when the preview includes another symbol.
4. Review the Git diff and run the relevant tests. Check remaining matches of the old name against their declarations.

## Implement a trait

Use `explain TraitName` for its contract, then `scaffold impl ClassName` for the methods the class still needs to implement. To see an existing implementation, run `overrides method --of TraitName --body`. If tests are part of the change, `scaffold test ClassName` prints a starting point.

## Investigate a behavior

Find the method with `search` or `def Owner.method`. Read `body method --in Owner --imports`, then follow an outgoing call or inspect a candidate caller. Use `tests "part of the test name"` to read a matching case.

## Review unused code

`unused com.example` lists candidates. Check uses inside the declaring file, test mentions and consumers outside the checkout. For a public API, also check how clients load the type. Remove a declaration only when removal is in scope and these checks support it.
