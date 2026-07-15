import {projectData} from "./fieldProjection";

describe("projectData", () => {
  it("returns data unchanged when no selection is given", () => {
    expect(projectData({a: 1, b: 2})).toEqual({a: 1, b: 2});
  });

  it("include keeps only the listed top-level keys", () => {
    expect(projectData({a: 1, b: 2, c: 3}, {include: ["a", "c"]})).toEqual({a: 1, c: 3});
  });

  it("exclude drops the listed top-level keys", () => {
    expect(projectData({a: 1, b: 2, c: 3}, {exclude: ["b"]})).toEqual({a: 1, c: 3});
  });

  it("include of a key that is absent simply yields nothing for it (no crash)", () => {
    expect(projectData({a: 1}, {include: ["a", "missing"]})).toEqual({a: 1});
  });

  it("passes non-object values through untouched (array / primitive / null)", () => {
    expect(projectData([1, 2, 3], {include: ["0"]})).toEqual([1, 2, 3]);
    expect(projectData("scalar" as unknown, {exclude: ["length"]})).toEqual("scalar");
    expect(projectData(null, {include: ["a"]})).toBeNull();
  });
});
