# Changelog

The plugin's published change notes come from the first section below.

## 1.*.*

- Fix Marketplace compatibility detection for the plugin's Compose dependency.

## 1.2.0

- Installation works on 2026.3 EAP after a compatibility issue with reopening recent projects previously prevented it.
- Even when typing outpaces list updates, <kbd>Enter</kbd> opens the project that matches the full query entered so far.
- <b>Settings | Tools | Project Switcher</b> offers a search field above the list as an alternative to speed search.
- The popup sorts open projects by last use, newest first, and preselects the previous project so that <kbd>Alt</kbd>+<kbd>F2</kbd>, then <kbd>Enter</kbd>, takes you back to it.
- Closing the current project with <kbd>Cmd</kbd>+<kbd>W</kbd> on macOS or <kbd>Ctrl</kbd>+<kbd>W</kbd> on other systems works with other projects open and returns focus to the previously used project.
- Long lists have a scrollbar and support <kbd>Page Up</kbd>, <kbd>Page Down</kbd>, <kbd>Home</kbd> and <kbd>End</kbd>, while selection changes scroll only far enough to keep the selected row visible.
- After a project is closed or removed from the popup, the list keeps its icons and scroll position without briefly showing a loading message.
- Search ignores the home-directory portion of paths, gives project-name matches priority over location matches, and displays the location when it is the sole match.
- Separate options in <b>Settings | Tools | Project Switcher</b> show the selected project's location and available shortcuts below the list.
- Choosing <kbd>Delete</kbd> and <kbd>Backspace</kbd> in <b>Settings | Tools | Project Switcher</b> replaces <kbd>Cmd</kbd>+<kbd>W</kbd> and <kbd>Ctrl</kbd>+<kbd>W</kbd> for closing or removing projects, with one project closed per press, an empty query required, and a selection change required after erasing a query.
- Recent projects open in the current window with <kbd>Shift</kbd>+click or in a new window with <kbd>Ctrl</kbd>+click, matching <kbd>Shift</kbd>+<kbd>Enter</kbd> and <kbd>Ctrl</kbd>+<kbd>Enter</kbd> respectively.
- Any keymap shortcut assigned to Switch Project toggles the popup closed on a second press, including shortcuts other than <kbd>Alt</kbd>+<kbd>F2</kbd>.
- From its first frame, the popup displays the cached project list with sharp icons, then updates both in the background.
- All project icons load concurrently rather than in groups of four, and Power Save Mode skips the wait for icons the IDE does not generate.
- A dimmed recent-project entry indicates that its directory is missing.
- To open a recent project on WSL or another non-local file system, the plugin uses the IDE's reopen action so the environment is prepared first.
- Open projects remain available in the popup if the IDE fails to assemble the recent-project list, rather than being replaced by an error.
- Remote development hosts no longer load the plugin because they cannot display its popup, and JetBrains Client no longer shows its action.

## 1.1.2

- The characters that matched the search are highlighted in project names, and in paths where those are shown.
- The popup responds to the keyboard again on 2026.2.3, where <kbd>Up</kbd>, <kbd>Down</kbd>, typing and the <kbd>Alt</kbd>+<kbd>F2</kbd> toggle had stopped working.

## 1.1.1

- Fix IntelliJ Platform binary compatibility regression.

## 1.1.0

- <kbd>Up</kbd> and <kbd>Down</kbd> now wrap around the list: moving up from the first row selects the last one, and moving down from the last row returns to the first.
- Rows that no longer fit are shortened with an ellipsis instead of being cut off: paths lose their middle, branches lose their prefix, and a long name can no longer crowd out the path and branch beside it.
- The first popup of an IDE session now opens with its project icons already in place, instead of having them fade in over the list. The lookup they need is done in the background shortly after a project window opens.
- Closing an open project or removing a recent one moved from <kbd>Delete</kbd> to <kbd>Cmd</kbd>+<kbd>W</kbd> on macOS and <kbd>Ctrl</kbd>+<kbd>W</kbd> elsewhere, and now works while a search query is being typed. <kbd>Delete</kbd> and <kbd>Backspace</kbd> only edit the query.
- Branch names no longer go missing when the popup has not been opened for a while. They now appear as soon as the IDE finishes looking them up, instead of only after a few reopens.

## 1.0.1

- The plugin description now lists every shortcut, including <kbd>Shift</kbd>+<kbd>Enter</kbd>, <kbd>Ctrl</kbd>+<kbd>Enter</kbd> and <kbd>Esc</kbd>.
- Fixed the screenshot links in the description, which still pointed at the repository's former name.

## 1.0.0

- Initial release.
- <kbd>Alt</kbd>+<kbd>F2</kbd> opens a popup listing open and recent projects; pressing it again closes the popup.
- Type to filter by project name or path, with typo tolerance and keyboard-layout correction.
- <kbd>Enter</kbd> opens the selection following the IDE's <b>Open project in</b> setting; <kbd>Shift</kbd>+<kbd>Enter</kbd> reuses the current window and <kbd>Ctrl</kbd>+<kbd>Enter</kbd> opens a new one.
- <kbd>Delete</kbd> and <kbd>Backspace</kbd> remove a recent project or close an open one while the search field is empty.
- Project icons are loaded asynchronously and sharpened on HiDPI displays.
- Projects that share a name are disambiguated by their path.
