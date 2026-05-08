# IntuLauncher

IntuLauncher is an Android home app prototype based on the presentation in `IntuLauncher Presentation.pdf`.

Current prototype goals:

- zero-setup first launch
- three large context-aware action slots
- three user-pinnable anchor slots
- ambient background changes based on live device context
- local-only recommendation logic using time, charging state, and audio output state

Project workflow:

- local Git repository
- Gitflow enabled with `master` and `develop`

Prototype notes:

- This first pass focuses on the launcher experience and interaction model.
- The AI recommendation layer described in the presentation is represented by a deterministic context engine so the UI and launcher behavior can be validated before adding ML or cloud inference.

